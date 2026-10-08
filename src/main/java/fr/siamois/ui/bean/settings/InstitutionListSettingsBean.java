package fr.siamois.ui.bean.settings;

import fr.siamois.domain.events.publisher.InstitutionChangeEventPublisher;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.institution.FailedInstitutionSaveException;
import fr.siamois.domain.models.exceptions.institution.InstitutionAlreadyExistException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.settings.components.SortState;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.institution.InstitutionDialogBean;
import fr.siamois.ui.bean.settings.institution.InstitutionMembersListBean;
import fr.siamois.utils.DateUtils;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import org.primefaces.event.SelectEvent;

import java.io.IOException;
import java.util.stream.Collectors;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class InstitutionListSettingsBean implements Serializable {

    private final transient InstitutionService institutionService;
    private final transient ProfilePermissionService profilePermissionService;
    private final SessionSettingsBean sessionSettingsBean;
    private final transient InstitutionChangeEventPublisher institutionChangeEventPublisher;
    private final InstitutionDialogBean institutionDialogBean;
    private final transient RecordingUnitService recordingUnitService;
    private final InstitutionDetailsBean institutionDetailsBean;
    private final InstitutionMembersListBean institutionMembersListBean;
    private final LangBean langBean;
    private final RedirectBean redirectBean;
    private final transient PersonService personService;


    private Set<InstitutionDTO> institutions = null;
    private List<InstitutionDTO> filteredInstitutions = null;
    private List<SortMeta> sortBy;
    private Map<Long, Boolean> toggleSwitchState = new HashMap<>();
    private Set<Long> institutionIdsCanActivate = new HashSet<>();
    private Set<Long> institutionIdsCanManageSettings = new HashSet<>();
    private Map<Long, Long> memberCountsByInstitutionId = new HashMap<>();
    private Map<Long, Long> recordingUnitCountsByInstitutionId = new HashMap<>();

    /** The column the list is sorted on. */
    private final SortState sort = new SortState("name");

    private static final java.util.Comparator<InstitutionDTO> BY_NAME =
            java.util.Comparator.comparing(InstitutionDTO::getName, String.CASE_INSENSITIVE_ORDER);

    private String filterText;

    /** A member filter chip added from "Ajouter un filtre" that has no value yet. */
    private boolean pendingMember;

    /** The row picked in the list (PrimeFaces single selection); picking a row opens its settings. */
    private InstitutionDTO selectedInstitution;

    /** Set when the list is filtered down to one member's institutions (see {@link #initFilteredByPerson}). */
    private PersonDTO filterPerson;

    /** Guards direct access to the institution list page: redirects to a 404 if the user cannot view institution data. */
    public void checkAccessOrRedirect() {
        if (!profilePermissionService.canViewInstitutionData(
                sessionSettingsBean.getUserInfo().getUser(), sessionSettingsBean.getSelectedInstitution())) {
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
        }
    }

    /** Opens the list on "my organisations": the organisations the current user belongs to. */
    public void init() {
        initFilteredByPerson(sessionSettingsBean.getAuthenticatedUser());
    }

    /** Every organisation, no member filter. */
    public void showAll() {
        filterPerson = null;
        loadInstitutions(institutionService.findAll());
    }

    /**
     * Loads the institutions of an arbitrary member instead of the full list, so the list can be reached
     * pre-filtered to "organisations this person belongs to" (e.g. from the instance user list).
     *
     * @param person the member whose institutions to show
     */
    public void initFilteredByPerson(PersonDTO person) {
        filterPerson = person;
        pendingMember = false;
        loadInstitutions(institutionService.findInstitutionsOfPerson(person));
    }

    public boolean isMemberFilterActive() {
        return filterPerson != null || pendingMember;
    }

    /** "Ajouter un filtre": puts the member chip on screen, to be given a value. */
    public void addFilter(String key) {
        if ("member".equals(key)) {
            pendingMember = true;
        }
    }

    /** Drops the member filter (the ✕ of its chip), whether it has a value yet or not. */
    public void clearMemberFilter() {
        pendingMember = false;
        if (filterPerson != null) {
            clearPersonFilter();
        }
    }

    /** Drops the person filter and reloads the full institution list. */
    public void clearPersonFilter() {
        showAll();
    }

    /** Backs the "my organisations" chip: toggles the member filter between the current user and cleared. */
    public void toggleFilterByMe() {
        if (isFilteredByMe()) {
            clearPersonFilter();
        } else {
            initFilteredByPerson(sessionSettingsBean.getAuthenticatedUser());
        }
    }

    /** Whether the member filter is currently set to the current user — highlights the "my organisations" chip. */
    public boolean isFilteredByMe() {
        PersonDTO me = sessionSettingsBean.getAuthenticatedUser();
        return filterPerson != null && me != null && filterPerson.getId().equals(me.getId());
    }

    private void loadInstitutions(Set<InstitutionDTO> loaded) {
        institutions = loaded;
        filteredInstitutions = new ArrayList<>(institutions);
        filterText = null;
        onFilterType();
        updateTogglesState();
        loadDerivedData();
        sortBy = new ArrayList<>();
        sortBy.add(SortMeta.builder()
                .field("active")
                .order(SortOrder.ASCENDING)
                .priority(1)
                .build());
    }

    /** Row-level permissions and counts, all computed in bulk to avoid an N+1 over {@link #institutions}. */
    private void loadDerivedData() {
        Set<InstitutionDTO> loaded = institutions == null ? Collections.emptySet() : institutions;
        PersonDTO person = sessionSettingsBean.getAuthenticatedUser();
        institutionIdsCanActivate = new HashSet<>(profilePermissionService.institutionIdsPersonCanAccess(person, loaded));
        institutionIdsCanManageSettings = new HashSet<>(profilePermissionService.institutionIdsPersonCanManageSettings(person, loaded));
        memberCountsByInstitutionId = new HashMap<>(institutionService.countMembersInInstitutions(loaded));
        List<Long> institutionIds = loaded.stream().map(InstitutionDTO::getId).toList();
        recordingUnitCountsByInstitutionId = new HashMap<>(recordingUnitService.countByInstitutionIds(institutionIds));
    }

    /** Autocomplete source for the person filter: matches by username or e-mail. */
    public List<PersonDTO> completePerson(String query) {
        return personService.findClosestByUsernameOrEmail(query);
    }

    public String displayDate(OffsetDateTime date) {
        return DateUtils.formatOffsetDateTime(date);
    }

    public void onFilterType() {
        boolean searching = filterText != null && filterText.length() > 2;
        filteredInstitutions = institutions.stream()
                .filter(institution -> !searching || institution.getName().toLowerCase().contains(filterText.toLowerCase()))
                .sorted(comparator())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Sorts the list on a column (a header's click), keeping the filters. */
    public void sortBy(String column) {
        sort.toggle(column);
        onFilterType();
    }

    private java.util.Comparator<InstitutionDTO> comparator() {
        java.util.Comparator<InstitutionDTO> byKey = switch (sort.getKey()) {
            case "rights" -> java.util.Comparator.comparing(this::canAccessInstitutionSettings);
            case "members" -> java.util.Comparator.comparingLong(this::numberOfMemberInInstitution);
            case "recordings" -> java.util.Comparator.comparingLong(this::numberOfRecordingUnitInInstitution);
            case "creationDate" -> java.util.Comparator.comparing(InstitutionDTO::getCreationDate,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
            default -> BY_NAME;
        };
        return sort.isAscending() ? byKey : byKey.reversed();
    }

    public void changeCurrentInstitution(InstitutionDTO institution) {
        log.trace("Change current institution received : {}", institution);
        sessionSettingsBean.setSelectedInstitution(institution);
        institutionChangeEventPublisher.publishInstitutionChangeEvent();
        updateTogglesState();
    }

    private void updateTogglesState() {
        InstitutionDTO selected = sessionSettingsBean.getSelectedInstitution();
        for (InstitutionDTO institution : institutions) {
            boolean isSelected = institution.getId().equals(selected.getId());
            toggleSwitchState.put(institution.getId(), isSelected);
        }
    }

    public boolean userCanCreateInstitution() {
        return profilePermissionService.hasInstancePermission(
                sessionSettingsBean.getAuthenticatedUser(), PermissionConstants.ORGANIZATION_CREATE);
    }

    public boolean canAccessInstitutionSettings(InstitutionDTO institution) {
        return institutionIdsCanManageSettings.contains(institution.getId());
    }

    /** Whether the current user may switch their active institution to this one — backs the row's toggle. */
    public boolean canActivateInstitution(InstitutionDTO institution) {
        return institutionIdsCanActivate.contains(institution.getId());
    }

    /** Whether the "active" toggle column is worth showing at all, i.e. the user can activate at least one institution. */
    public boolean hasAnyActivatableInstitution() {
        return !institutionIdsCanActivate.isEmpty();
    }

    public void displayCreateDialog() {
        log.trace("Display create institution dialog");
        institutionDialogBean.reset();
        institutionDialogBean.setTitle(langBean.msg("organisationManagement.create"));
        institutionDialogBean.setButtonLabel(langBean.msg("organisationManagement.dialog.create"));
        institutionDialogBean.setActionFromBean(this::createInstitution);
        PrimeFaces.current().ajax().update("newInstitutionDialog");
        PrimeFaces.current().executeScript("PF('newInstitutionDialog').show();");
    }

    public void createInstitution() {
        InstitutionDTO institution;
        institutionDialogBean.setThesaurusError(false);

        try {
            institution = institutionDialogBean.createInstitution();
            MessageUtils.displayInfoMessage(langBean, "common.entity.institution.created", institution.getName());
        } catch (NotSiamoisThesaurusException e) {
            institutionDialogBean.setThesaurusError(true);
            MessageUtils.displayErrorMessage(langBean, "myProfile.thesaurus.siamois.invalid");
            return;
        } catch (InstitutionAlreadyExistException e) {
            log.error("Institution already exists");
            MessageUtils.displayErrorMessage(langBean, "common.entity.institution.error.alreadyExist");
            return;
        } catch (FailedInstitutionSaveException e) {
            log.error("Failed to create institution", e);
            MessageUtils.displayErrorMessage(langBean, "common.error.internal");
            return;
        } catch (InvalidEndpointException e) {
            log.error("Invalid thesaurus url", e);
            institutionDialogBean.setThesaurusError(true);
            MessageUtils.displayErrorMessage(langBean, "common.error.thesaurusConfig.invalidUrl");
            return;
        }

        institutions.add(institution);
        filteredInstitutions.add(institution);
        toggleSwitchState.put(institution.getId(), false);

        PersonDTO person = sessionSettingsBean.getAuthenticatedUser();
        if (profilePermissionService.canAccessInstitution(person, institution)) {
            institutionIdsCanActivate.add(institution.getId());
        }
        if (profilePermissionService.hasInstancePermission(person, PermissionConstants.INSTANCE_MANAGE_SETTINGS)
                || profilePermissionService.hasOrganizationPermission(person, institution, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS)) {
            institutionIdsCanManageSettings.add(institution.getId());
        }
        memberCountsByInstitutionId.put(institution.getId(), institutionService.countMembersInInstitution(institution));
        recordingUnitCountsByInstitutionId.put(institution.getId(), recordingUnitService.countByInstitutionId(institution.getId()));

        institutionDialogBean.reset();
        PrimeFaces.current().executeScript("PF('newInstitutionDialog').hide();");
    }

    public long numberOfMemberInInstitution(InstitutionDTO institution) {
        return memberCountsByInstitutionId.getOrDefault(institution.getId(), 0L);
    }

    public long numberOfRecordingUnitInInstitution(InstitutionDTO institution) {
        return recordingUnitCountsByInstitutionId.getOrDefault(institution.getId(), 0L);
    }

    /** A click on a row (only rows the user may open are selectable): opens the organisation's settings. */
    public void onRowSelect(SelectEvent<InstitutionDTO> event) throws IOException {
        selectedInstitution = null;
        String path = redirectToInstitution(event.getObject());
        if (path == null) {
            return;
        }
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        externalContext.redirect(externalContext.getRequestContextPath() + path);
    }

    public String redirectToInstitution(InstitutionDTO institution) {
        if (!canAccessInstitutionSettings(institution)) {
            log.warn("Person {} tried to access settings of institution {} without permission",
                    sessionSettingsBean.getAuthenticatedUser(), institution.getId());
            MessageUtils.displayWarnMessage(langBean, "common.error.forbidden");
            return null;
        }
        institutionDetailsBean.setInstitution(institution);
        institutionDetailsBean.init();
        return "/pages/settings/institutionSettings.xhtml?faces-redirect=true";
    }

    /** Navigates straight to the organisation's member page — backs the row's "members" chip. */
    public String redirectToInstitutionMembers(InstitutionDTO institution) {
        if (!canAccessInstitutionSettings(institution)) {
            log.warn("Person {} tried to access members of institution {} without permission",
                    sessionSettingsBean.getAuthenticatedUser(), institution.getId());
            MessageUtils.displayWarnMessage(langBean, "common.error.forbidden");
            return null;
        }
        institutionDetailsBean.setInstitution(institution);
        institutionMembersListBean.init(institution);
        return "/pages/settings/institution/institutionMembersSettings.xhtml?faces-redirect=true";
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        institutions = null;
        filteredInstitutions = null;
        sortBy = null;
        toggleSwitchState.clear();
        institutionIdsCanActivate.clear();
        institutionIdsCanManageSettings.clear();
        memberCountsByInstitutionId.clear();
        recordingUnitCountsByInstitutionId.clear();
        filterText = null;
        filterPerson = null;
    }

}
