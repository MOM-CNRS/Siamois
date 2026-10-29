package fr.siamois.ui.bean.settings.project;


import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.SettingsDatatableBean;
import fr.siamois.ui.bean.settings.components.SortState;
import fr.siamois.utils.MessageUtils;
import fr.siamois.utils.context.ExecutionContextHolder;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.event.SelectEvent;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ProjectListBean implements SettingsDatatableBean {

    private final transient ActionUnitService actionUnitService;
    private final NavBean navBean;
    private final transient SessionSettingsBean sessionSettingsBean;
    private final transient ProjectDetailsBean projectDetailsBean;
    private final transient ProjectMembersListBean projectMembersListBean;
    private final transient InstitutionService institutionService;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient PersonService personService;
    private final LangBean langBean;
    private String searchInput;

    private Set<ActionUnitDTO> actionUnits;
    private List<ActionUnitDTO> filteredActionUnits;
    private Map<Long, Integer> memberCountsByActionUnitId = Collections.emptyMap();
    private Set<Long> actionUnitIdsWithManageSettingsPermission = Collections.emptySet();

    /** The column the list is sorted on. */
    private final SortState sort = new SortState("name");

    /** The organisation the list is narrowed to, or null for all of them. */
    private Long filterInstitutionId;

    /** A filter chip added from "Ajouter un filtre" that has no value yet — it stays on screen while it is filled in. */
    private boolean pendingInstitution;
    private boolean pendingMember;

    /** The row picked in the list (PrimeFaces single selection); picking a row opens its settings. */
    private ActionUnitDTO selectedActionUnit;

    /** Set when the list is filtered down to one member's projects (see {@link #initFilteredByPerson}). */
    private PersonDTO filterPerson;

    @Override
    public void add() {
        throw new UnsupportedOperationException("Adding action units is not supported in this context.");
    }

    @Override
    public void filter() {
        String search = searchInput == null ? "" : searchInput.toLowerCase();
        filteredActionUnits = actionUnits.stream()
                .filter(actionUnit -> search.isEmpty() || actionUnit.getName().toLowerCase().contains(search))
                .filter(actionUnit -> filterInstitutionId == null
                        || (actionUnit.getCreatedByInstitution() != null
                        && filterInstitutionId.equals(actionUnit.getCreatedByInstitution().getId())))
                .sorted(comparator())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Sorts the list on a column (a header's click), keeping the filters.
     */
    public void sortBy(String column) {
        sort.toggle(column);
        filter();
    }

    /**
     * The comparator of the current sort column and direction.
     */
    private Comparator<ActionUnitDTO> comparator() {
        Comparator<String> text = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        Comparator<ActionUnitDTO> byKey = switch (sort.getKey()) {
            case "rights" -> Comparator.comparing(this::canManageSettings);
            case "organisation" -> Comparator.comparing(
                    au -> au.getCreatedByInstitution() == null ? null : au.getCreatedByInstitution().getName(), text);
            case "identifier" -> Comparator.comparing(ActionUnitDTO::getFullIdentifier, text);
            case "members" -> Comparator.comparingInt(this::numberOfMemberInActionUnit);
            case "manager" -> Comparator.comparing(
                    au -> au.getCreatedBy() == null ? null : au.getCreatedBy().displayName(), text);
            default -> Comparator.comparing(ActionUnitDTO::getName, text);
        };
        return sort.isAscending() ? byKey : byKey.reversed();
    }

    /**
     * The name of the organisation the list is narrowed to — the label of its filter chip.
     */
    public String getFilterInstitutionName() {
        return getInstitutionOptions().stream()
                .filter(institution -> institution.getId().equals(filterInstitutionId))
                .map(InstitutionDTO::getName)
                .findFirst()
                .orElse("");
    }

    /**
     * Drops the organisation filter (the ✕ of its chip).
     */
    public void clearInstitutionFilter() {
        filterInstitutionId = null;
        pendingInstitution = false;
        filter();
    }

    /**
     * Drops the member filter (the ✕ of its chip), whether it has a value yet or not.
     */
    public void clearMemberFilter() {
        pendingMember = false;
        if (filterPerson != null) {
            clearPersonFilter();
        }
    }

    /**
     * "Ajouter un filtre": puts the chip of that filter on screen, to be given a value.
     */
    public void addFilter(String key) {
        if ("organisation".equals(key)) {
            pendingInstitution = true;
        } else if ("member".equals(key)) {
            pendingMember = true;
        }
    }

    public boolean isInstitutionFilterActive() {
        return filterInstitutionId != null || pendingInstitution;
    }

    public boolean isMemberFilterActive() {
        return filterPerson != null || pendingMember;
    }

    /**
     * Whether "Ajouter un filtre" still has something to offer.
     */
    public boolean isCanAddFilter() {
        return !isInstitutionFilterActive() || !isMemberFilterActive();
    }

    /**
     * The organisations of the loaded projects, for the organisation filter — plus the active organisation,
     * which is the default filter even when none of the loaded projects belongs to it.
     */
    public List<InstitutionDTO> getInstitutionOptions() {
        if (actionUnits == null) {
            return List.of();
        }
        return Stream.concat(
                        actionUnits.stream().map(ActionUnitDTO::getCreatedByInstitution),
                        Stream.of(sessionSettingsBean.getSelectedInstitution()))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(InstitutionDTO::getId, i -> i, (a, b) -> a))
                .values().stream()
                .sorted(Comparator.comparing(InstitutionDTO::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public int numberOfMemberInActionUnit(ActionUnitDTO actionUnit) {
        return memberCountsByActionUnitId.getOrDefault(actionUnit.getId(), 0);
    }

    /**
     * Whether the current user may reach this project's settings — backs the row's burger button.
     */
    public boolean canManageSettings(ActionUnitDTO actionUnit) {
        return actionUnitIdsWithManageSettingsPermission.contains(actionUnit.getId());
    }

    public void reset() {
        this.searchInput = null;
        this.actionUnits = null;
        this.filteredActionUnits = null;
        this.filterPerson = null;
        this.filterInstitutionId = null;
        this.pendingInstitution = false;
        this.pendingMember = false;
        this.memberCountsByActionUnitId = Collections.emptyMap();
        this.actionUnitIdsWithManageSettingsPermission = Collections.emptySet();
    }

    /**
     * Opens the list on "my projects" of the active organisation.
     */
    public void init() {
        initFilteredByPerson(sessionSettingsBean.getAuthenticatedUser());
    }

    /**
     * Loads every project the current user can edit, no member filter.
     */
    private void loadAllEditable() {
        UserInfo info = ExecutionContextHolder.getNonNull();
        this.actionUnits = actionUnitService.findAllEditableByPerson(info.getUser());
        filter();
        loadDerivedData();
    }

    /**
     * Loads the projects of an arbitrary member instead of the current admin's own editable ones, so the
     * list can be reached pre-filtered to "projects this person belongs to" (e.g. from the instance user list).
     * <p>
     * The list opens narrowed to the active organization.
     *
     * @param person the member whose projects to show
     */
    public void initFilteredByPerson(PersonDTO person) {
        reset();
        InstitutionDTO activeInstitution = sessionSettingsBean.getSelectedInstitution();
        this.filterInstitutionId = activeInstitution == null ? null : activeInstitution.getId();
        loadByPerson(person);
    }

    /**
     * Loads the projects the given person is a team member of, then applies the filters.
     */
    private void loadByPerson(PersonDTO person) {
        this.filterPerson = person;
        this.actionUnits = new HashSet<>(actionUnitService.findAllByTeamMember(person));
        filter();
        loadDerivedData();
    }

    /**
     * Member counts and the row-level manage-settings permission, both computed in bulk to avoid an N+1.
     */
    private void loadDerivedData() {
        this.memberCountsByActionUnitId = institutionService.countMembersOf(actionUnits);
        List<Long> actionUnitIds = actionUnits.stream().map(ActionUnitDTO::getId).toList();
        this.actionUnitIdsWithManageSettingsPermission = profilePermissionService.actionUnitIdsWithProjectSettingsPermission(
                sessionSettingsBean.getUserInfo(), actionUnitIds);
    }

    /**
     * Drops the person filter and reloads the current admin's own full editable project list, keeping the organisation filter.
     */
    public void clearPersonFilter() {
        keepingInstitutionFilter(this::loadAllEditable);
    }

    /**
     * Changes the member filter (the member chip's autocomplete), keeping the organisation filter.
     */
    public void filterByPerson(PersonDTO person) {
        keepingInstitutionFilter(() -> loadByPerson(person));
    }

    /**
     * Resets the list and reloads it with {@code load}, carrying the organisation filter over.
     */
    private void keepingInstitutionFilter(Runnable load) {
        Long institutionId = filterInstitutionId;
        boolean pending = pendingInstitution;
        reset();
        this.filterInstitutionId = institutionId;
        this.pendingInstitution = pending;
        load.run();
    }

    /**
     * Backs the "my projects" chip: toggles the member filter between the current user and cleared.
     */
    public void toggleFilterByMe() {
        if (isFilteredByMe()) {
            clearPersonFilter();
        } else {
            filterByPerson(sessionSettingsBean.getAuthenticatedUser());
        }
    }

    /**
     * Whether the member filter is currently set to the current user — highlights the "my projects" chip.
     */
    public boolean isFilteredByMe() {
        PersonDTO me = sessionSettingsBean.getAuthenticatedUser();
        return filterPerson != null && me != null && filterPerson.getId().equals(me.getId());
    }

    /**
     * Autocomplete source for the person filter: matches by username or e-mail.
     */
    public List<PersonDTO> completePerson(String query) {
        return personService.findClosestByUsernameOrEmail(query);
    }


    /**
     * A click on a row (only rows the user may open are selectable): opens the project's settings.
     */
    public void onRowSelect(SelectEvent<ActionUnitDTO> event) throws IOException {
        selectedActionUnit = null;
        String path = redirectToProject(event.getObject());
        if (path == null) {
            return;
        }
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        externalContext.redirect(externalContext.getRequestContextPath() + path);
    }

    public String redirectToProject(ActionUnitDTO actionUnit) {
        if (!profilePermissionService.hasProjectSettingsPermission(
                sessionSettingsBean.getAuthenticatedUser(), actionUnit)) {
            log.warn("Person {} tried to access settings of project {} without permission",
                    sessionSettingsBean.getAuthenticatedUser(), actionUnit.getId());
            MessageUtils.displayWarnMessage(langBean, "common.error.forbidden");
            return null;
        }
        projectDetailsBean.setProject(actionUnit);
        projectDetailsBean.init();
         return "/pages/settings/project/projectSettings.xhtml?faces-redirect=true";
    }

    /**
     * Navigates straight to the project's member page — backs the row's "members" chip.
     */
    public String redirectToProjectMembers(ActionUnitDTO actionUnit) {
        if (!profilePermissionService.hasProjectSettingsPermission(
                sessionSettingsBean.getAuthenticatedUser(), actionUnit)) {
            log.warn("Person {} tried to access members of project {} without permission",
                    sessionSettingsBean.getAuthenticatedUser(), actionUnit.getId());
            MessageUtils.displayWarnMessage(langBean, "common.error.forbidden");
            return null;
        }
        projectDetailsBean.setProject(actionUnit);
        projectMembersListBean.init(actionUnit);
        return "/pages/settings/project/projectMembersSettings.xhtml?faces-redirect=true";
    }
}
