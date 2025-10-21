package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.history.ActionUnitHist;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.HistoryService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.dialog.document.DocumentCreationBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.single.tab.RecordingTab;
import fr.siamois.ui.bean.panel.models.panel.single.tab.SpecimenTab;
import fr.siamois.ui.bean.settings.team.TeamMembersBean;
import fr.siamois.ui.lazydatamodel.RecordingUnitInActionUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.SpecimenInActionUnitLazyDataModel;
import fr.siamois.utils.MessageUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>This bean handles the spatial unit page</p>
 *
 * @author Grégory Bliault
 */
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Slf4j
@Data
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ActionUnitPanel extends AbstractSingleEntityPanel<ActionUnit, ActionUnitHist> implements Serializable {

    // Deps

    private final LangBean langBean;

    private final transient FieldService fieldService;
    private final RedirectBean redirectBean;
    private final transient LabelService labelService;
    private final TeamMembersBean teamMembersBean;
    private final transient HistoryService historyService;
    private final transient RecordingUnitService recordingUnitService;
    private final transient SpecimenService specimenService;


    // For entering new code
    private ActionCode newCode;
    private Integer newCodeIndex; // Index of the new code, if primary: 0, otherwise 1 to N
    // (but corresponds to 0 to N-1 in secondary code list)

    // Field related
    private Boolean editType;
    private Concept fType;

    @Override
    protected boolean documentExistsInUnitByHash(ActionUnit unit, String hash) {
        return documentService.existInActionUnitByHash(unit, hash);
    }

    @Override
    protected void addDocumentToUnit(Document doc, ActionUnit unit) {
        documentService.addToActionUnit(doc, unit);
    }


    private transient List<ActionCode> secondaryActionCodes;

    // Linked recording units
    private transient RecordingUnitInActionUnitLazyDataModel recordingUnitListLazyDataModel;
    private Integer totalRecordingUnitCount;
    // Lazy model for recording unit in the spatial unit
    private SpecimenInActionUnitLazyDataModel specimenLazyDataModel;
    private Integer totalSpecimenCount;


    public ActionUnitPanel(LangBean langBean,
                           FieldService fieldService, RedirectBean redirectBean,
                           LabelService labelService, TeamMembersBean teamMembersBean,
                           DocumentCreationBean documentCreationBean,
                           HistoryService historyService, RecordingUnitService recordingUnitService,
                           AbstractSingleEntity.Deps deps, SpecimenService specimenService) {
        super("Unité d'action", "bi bi-arrow-down-square", "siamois-panel action-unit-panel single-panel",
                documentCreationBean, deps);

        this.langBean = langBean;
        this.fieldService = fieldService;
        this.redirectBean = redirectBean;
        this.labelService = labelService;
        this.teamMembersBean = teamMembersBean;
        this.historyService = historyService;
        this.recordingUnitService = recordingUnitService;
        this.specimenService = specimenService;
    }


    @Override
    public String ressourceUri() {
        return String.format("/actionunit/%s", unit.getId());
    }



    public void refreshUnit() {

        // reinit
        hasUnsavedModifications = false;
        errorMessage = null;
        unit = null;
        newCode = new ActionCode();
        secondaryActionCodes = new ArrayList<>();

        try {

            unit = actionUnitService.findById(idunit);
            this.setTitleCodeOrTitle(unit.getName()); // Set panel title
            backupClone = new ActionUnit(unit);
            this.titleCodeOrTitle = unit.getName();
            secondaryActionCodes = new ArrayList<>(unit.getSecondaryActionCodes());
            fType = this.unit.getType();

            initForms();


            // Get all the CHILDREN of the spatial unit
            selectedCategoriesChildren = new ArrayList<>();
            totalChildrenCount = 0;
            // Get all the Parentsof the spatial unit
            selectedCategoriesParents = new ArrayList<>();
            totalParentsCount = 0;


        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load action unit: " + e.getMessage();
        }


        historyVersion = historyService.findActionUnitHistory(unit);
        documents = documentService.findForActionUnit(unit);
    }

    @Override
    public void init() {
        try {

            if (idunit == null) {
                this.errorMessage = "The ID of the spatial unit must be defined";
                return;
            }

            refreshUnit();

            if (this.unit == null) {
                log.error("The Action Unit page should not be accessed without ID or by direct page path");
                errorMessage = "The Action Unit page should not be accessed without ID or by direct page path";
            }

            recordingUnitListLazyDataModel = new RecordingUnitInActionUnitLazyDataModel(
                    recordingUnitService,
                    sessionSettingsBean,
                    langBean,
                    unit
            );
            recordingUnitListLazyDataModel.setSelectedUnits(new ArrayList<>());


            specimenLazyDataModel = new SpecimenInActionUnitLazyDataModel(
                    specimenService,
                    langBean,
                    unit
            );
            specimenLazyDataModel.setSelectedUnits(new ArrayList<>());

            totalSpecimenCount = specimenService.countByActionContext(unit);
            totalRecordingUnitCount = recordingUnitService.countByActionContext(unit);
            RecordingTab recordingTab = new RecordingTab(
                    "common.entity.recordingUnits",
                    "bi bi-pencil-square",
                    "recordingTab",
                    recordingUnitListLazyDataModel,
                    totalRecordingUnitCount);
            SpecimenTab specimenTab = new SpecimenTab(
                    "common.entity.specimens",
                    "bi bi-bucket",
                    "specimenTab",
                    specimenLazyDataModel,
                    totalSpecimenCount);
            tabs.add(recordingTab);
            tabs.add(specimenTab);

        } catch (
                ActionUnitNotFoundException e) {
            log.error("Action unit with id {} not found", idunit);
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
        } catch (
                RuntimeException e) {
            this.errorMessage = "Failed to load action unit: " + e.getMessage();
            redirectBean.redirectTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }


    }

    @Override
    public List<Person> authorsAvailable() {
        return List.of();
    }

    @Override
    public void initForms() {

        overviewForm = ActionUnit.OVERVIEW_FORM;
        detailsForm = ActionUnit.DETAILS_FORM;
        // Init system form answers
        formResponse = initializeFormResponse(detailsForm, unit);

    }

    @Override
    protected String getFormScopePropertyName() {
        return "";
    }

    @Override
    protected void setFormScopePropertyValue(Concept concept) {
        // to be implemented
    }


    @Override
    public void cancelChanges() {
        unit.setName(backupClone.getName());
        unit.setValidated(backupClone.getValidated());
        unit.setType(backupClone.getType());
        hasUnsavedModifications = false;
        initForms();
    }

    @Override
    public void visualise(ActionUnitHist history) {
        // TODO: implement
    }

    @Override
    public void saveDocument() {
        // TODO : implement
    }

    @Override
    public boolean save(Boolean validated) {

        updateJpaEntityFromFormResponse(formResponse, unit);
        unit.setValidated(validated);
        try {
            actionUnitService.save(unit);
        } catch (FailedActionUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.actionUnits.updateFailed", unit.getFullIdentifier());
            return false;
        }

        refreshUnit();
        MessageUtils.displayInfoMessage(langBean, "common.entity.actionUnits.updated", unit.getFullIdentifier());
        return true;
    }


    public void saveNewActionCode() {
        // Update the action code
        if (newCodeIndex == 0) {
            // update primary action code
            unit.setPrimaryActionCode(newCode);
        } else if (newCodeIndex > 0) {
            unit.getSecondaryActionCodes().add(newCode);
            secondaryActionCodes.set(newCodeIndex - 1, newCode);
        }
    }


    /**
     * Fetch the autocomplete results for the action codes
     *
     * @param input the input of the user
     * @return the list of codes the input to display in the autocomplete
     */
    public List<ActionCode> completeActionCode(String input) {

        return actionUnitService.findAllActionCodeByCodeIsContainingIgnoreCase(input);

    }

    public String getUrlForActionCodeTypeFieldCode() {
        return fieldConfigurationService.getUrlForFieldCode(sessionSettingsBean.getUserInfo(), ActionCode.TYPE_FIELD_CODE);
    }

    /**
     * Fetch the autocomplete results on API for the action code type field
     *
     * @param input the input of the user
     * @return the list of concepts that match the input to display in the autocomplete
     */
    public List<Concept> completeActionCodeType(String input) {

        try {
            return fieldConfigurationService.fetchAutocomplete(sessionSettingsBean.getUserInfo(), ActionCode.TYPE_FIELD_CODE, input);
        } catch (NoConfigForFieldException e) {
            log.error(e.getMessage(), e);
            return new ArrayList<>();
        }

    }

    public void handleSelectPrimaryCode() {
        // To implement
    }

    @Override
    public String displayHeader() {
        return "/panel/header/actionUnitPanelHeader.xhtml";
    }

    public void addNewSecondaryCode() {
        ActionCode code = new ActionCode();
        Concept c = new Concept();
        code.setCode("");
        code.setType(c);
        secondaryActionCodes.add(code);
    }

    @Override
    public String getAutocompleteClass() {
        return "action-unit-autocomplete";
    }

    public void initNewActionCode(int index) {
        newCodeIndex = index;
        newCode = new ActionCode();
    }

    public void removeSecondaryCode(int index) {
        secondaryActionCodes.remove(index);
    }

    public String getFormattedValue(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof Number) {
            // Integer or Number case
            return value.toString();
        } else if (value instanceof List<?> list) {
            // Handle list of concepts
            String langCode = sessionSettingsBean.getLanguageCode();
            return list.stream()
                    .map(item -> (item instanceof Concept concept) ? labelService.findLabelOf(concept, langCode).getValue() : item.toString())
                    .collect(Collectors.joining(", "));
        }

        return value.toString(); // Default case
    }


    public static class ActionUnitPanelBuilder {

        private final ActionUnitPanel actionUnitPanel;

        public ActionUnitPanelBuilder(ObjectProvider<ActionUnitPanel> actionUnitPanelProvider) {
            this.actionUnitPanel = actionUnitPanelProvider.getObject();
        }

        public ActionUnitPanelBuilder id(Long id) {
            actionUnitPanel.setIdunit(id);
            return this;
        }

        public ActionUnitPanelBuilder breadcrumb(PanelBreadcrumb breadcrumb) {
            actionUnitPanel.setBreadcrumb(breadcrumb);

            return this;
        }

        public ActionUnitPanelBuilder activeIndex(Integer id) {
            actionUnitPanel.setActiveTabIndex(id);
            return this;
        }


        public ActionUnitPanel build() {
            actionUnitPanel.init();
            return actionUnitPanel;
        }
    }

    public void goToMemberList() {
        redirectBean.redirectTo(String.format("/settings/organisation/actionunit/%s/members", unit.getId()));
    }

}