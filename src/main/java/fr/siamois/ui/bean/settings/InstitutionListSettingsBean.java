package fr.siamois.ui.bean.settings;

import fr.siamois.domain.events.publisher.InstitutionChangeEventPublisher;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.institution.FailedInstitutionSaveException;
import fr.siamois.domain.models.exceptions.institution.InstitutionAlreadyExistException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.institution.InstitutionDialogBean;
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
import org.springframework.stereotype.Component;

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
    private final SessionSettingsBean sessionSettingsBean;
    private final transient InstitutionChangeEventPublisher institutionChangeEventPublisher;
    private final InstitutionDialogBean institutionDialogBean;
    private final transient RecordingUnitService recordingUnitService;
    private final InstitutionDetailsBean institutionDetailsBean;
    private final LangBean langBean;
    private final RedirectBean redirectBean;
    private Set<Institution> institutions = null;
    private List<Institution> filteredInstitutions = null;
    private List<SortMeta> sortBy;
    private Map<Long, Boolean> toggleSwitchState = new HashMap<>();

    private String filterText;

    public void init() {
        UserInfo info = sessionSettingsBean.getUserInfo();
        institutions = institutionService.findInstitutionsOfPerson(info.getUser());
        filteredInstitutions = new ArrayList<>(institutions);
        onFilterType();
        updateTogglesState();
        sortBy = new ArrayList<>();
        sortBy.add(SortMeta.builder()
                .field("active")
                .order(SortOrder.ASCENDING)
                .priority(1)
                .build());
    }

    public String displayDate(OffsetDateTime date) {
        return DateUtils.formatOffsetDateTime(date);
    }

    public void onFilterType() {
        if (filterText != null && !filterText.isEmpty() && filterText.length() > 2) {
            filteredInstitutions = institutions.stream()
                    .filter(institution -> institution.getName().toLowerCase().contains(filterText.toLowerCase()))
                    .toList();
        } else {
            filteredInstitutions = new ArrayList<>(institutions);
        }
    }

    public void changeCurrentInstitution(Institution institution) {
        log.trace("Change current institution received : {}", institution);
        sessionSettingsBean.setSelectedInstitution(institution);
        institutionChangeEventPublisher.publishInstitutionChangeEvent();
        updateTogglesState();
    }

    private void updateTogglesState() {
        Institution selected = sessionSettingsBean.getSelectedInstitution();
        for (Institution institution : institutions) {
            boolean isSelected = institution.getId().equals(selected.getId());
            toggleSwitchState.put(institution.getId(), isSelected);
        }
    }

    public boolean userIsSuperadmin() {
        return sessionSettingsBean.getAuthenticatedUser().isSuperAdmin();
    }

    public boolean hasMoreThenOneInstitution() {
        if (institutions == null) {
            UserInfo info = sessionSettingsBean.getUserInfo();
            institutions = institutionService.findInstitutionsOfPerson(info.getUser());
            return false;
        }
        return institutions.size() > 1;
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
        Institution institution;
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
        institutionDialogBean.reset();
        PrimeFaces.current().executeScript("PF('newInstitutionDialog').hide();");
    }

    public long numberOfMemberInInstitution(Institution institution) {
        return institutionService.countMembersInInstitution(institution);
    }

    public long numberOfRecordingUnitInInstitution(Institution institution) {
        return recordingUnitService.countByInstitution(institution);
    }

    public String redirectToInstitution(Institution institution) {
        institutionDetailsBean.setInstitution(institution);
        institutionDetailsBean.init();
        return "/pages/settings/institutionSettings.xhtml?faces-redirect=true";
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        institutions = null;
        filteredInstitutions = null;
        sortBy = null;
        toggleSwitchState.clear();
        filterText = null;
    }

}
