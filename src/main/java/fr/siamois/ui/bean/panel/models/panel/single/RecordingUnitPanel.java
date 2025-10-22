package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.actionunit.ActionUnitFormMapping;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectMultiple;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectMultiple;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.dialog.document.DocumentCreationBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.single.tab.SpecimenTab;
import fr.siamois.ui.lazydatamodel.RecordingUnitChildrenLazyDataModel;
import fr.siamois.ui.lazydatamodel.RecordingUnitParentsLazyDataModel;
import fr.siamois.ui.lazydatamodel.SpecimenInRecordingUnitLazyDataModel;
import fr.siamois.utils.MessageUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

@Slf4j
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RecordingUnitPanel extends AbstractSingleMultiHierarchicalEntityPanel<RecordingUnit>  implements Serializable {

    // Deps
    protected final transient LangBean langBean;
    protected final transient RecordingUnitService recordingUnitService;
    protected final transient PersonService personService;
    private final transient RedirectBean redirectBean;
    protected final transient ConceptService conceptService;
    private final transient SpecimenService specimenService;
    private final transient FormService formService;

    // ---------- Locals
    // RU
    protected RecordingUnit recordingUnit;

    // Form
    protected CustomForm additionalForm;

    // Linked specimen
    private transient SpecimenInRecordingUnitLazyDataModel specimenListLazyDataModel ;

    // lazy model for children
    private RecordingUnitChildrenLazyDataModel lazyDataModelChildren ;
    // lazy model for parents
    private RecordingUnitParentsLazyDataModel lazyDataModelParents ;

    protected RecordingUnitPanel(LangBean langBean,
                                 RecordingUnitService recordingUnitService,
                                 PersonService personService, ConceptService conceptService,
                                 DocumentCreationBean documentCreationBean,
                                 RedirectBean redirectBean,
                                 AbstractSingleEntity.Deps deps,
                                 SpecimenService specimenService, HistoryAuditService historyAuditService, FormService formService)  {

        super("common.entity.recordingunit",
                "bi bi-pencil-square",
                "siamois-panel recording-unit-panel single-panel",
                documentCreationBean, deps, historyAuditService);
        this.langBean = langBean;
        this.recordingUnitService = recordingUnitService;
        this.personService = personService;
        this.conceptService = conceptService;
        this.redirectBean = redirectBean;
        this.specimenService = specimenService;
        this.formService = formService;
    }





    @Override
    public String ressourceUri() {
        return "/recordingunit/" + idunit;
    }

    @Override
    public String displayHeader() {
        return "/panel/header/recordingUnitPanelHeader.xhtml";
    }

    @Override
    public List<SpatialUnit> getSpatialUnitOptions() {

        // Return the spatial context of the parent action
        if(unit.getActionUnit() != null) {
            return new ArrayList<>(unit.getActionUnit().getSpatialContext());
        }

        return List.of();
    }

    @Override
    protected String getFormScopePropertyName() {
        return "type";
    }

    @Override
    protected void setFormScopePropertyValue(Concept concept) {
        unit.setType(concept);
    }

    public LocalDate offsetDateTimeToLocalDate(OffsetDateTime offsetDT) {
        return offsetDT.toLocalDate();
    }

    public List<Concept> fetchChildrenOfConcept(Concept concept) {
        List<Concept> concepts;

        try {
            concepts = conceptService.findDirectSubConceptOf(concept);
        } catch (ErrorProcessingExpansionException e) {
            log.error(e.getMessage());
            log.debug(e.getMessage(), e);
            return new ArrayList<>();
        }

        return concepts;

    }

    public void initializeAnswer(CustomField field) {
        if (recordingUnit.getFormResponse().getAnswers().get(field) == null) {
            // Init missing parameters
            if (field instanceof CustomFieldSelectMultiple) {
                recordingUnit.getFormResponse().getAnswers().put(field, new CustomFieldAnswerSelectMultiple());
            } else if (field instanceof CustomFieldInteger) {
                recordingUnit.getFormResponse().getAnswers().put(field, new CustomFieldAnswerInteger());
            }
        }
    }

    public void changeCustomForm() {
        // Do we have a form available for the selected type?
        Set<ActionUnitFormMapping> formsAvailable = recordingUnit.getActionUnit().getFormsAvailable();
        additionalForm = getFormForRecordingUnitType(recordingUnit.getType(), formsAvailable);
        if (recordingUnit.getFormResponse() == null) {
            recordingUnit.setFormResponse(new CustomFormResponse());
            recordingUnit.getFormResponse().setAnswers(new HashMap<>());
        }
        recordingUnit.getFormResponse().setForm(additionalForm);
        if (additionalForm != null) {
            initFormResponseAnswers();
        }


    }

    public CustomForm getFormForRecordingUnitType(Concept type, Set<ActionUnitFormMapping> availableForms) {
        return availableForms.stream()
                .filter(mapping -> mapping.getPk().getConcept().equals(type) // Vérifier le concept
                        && "RECORDING_UNIT" .equals(mapping.getPk().getTableName())) // Vérifier le tableName
                .map(mapping -> mapping.getPk().getForm())
                .findFirst()
                .orElse(null); // Retourner null si aucun match
    }



    public void initFormResponseAnswers() {


        if (recordingUnit.getFormResponse().getForm() != null) {
            recordingUnit.getFormResponse().getForm().getLayout().stream()
                    .flatMap(section -> section.getRows().stream())      // Stream rows in each section
                    .flatMap(row -> row.getColumns().stream())           // Stream columns in each row
                    .map(CustomCol::getField)                    // Extract the field from each column
                    .forEach(this::initializeAnswer);                    // Process each field
        }


    }

    public OffsetDateTime localDateToOffsetDateTime(LocalDate localDate) {
        return localDate.atTime(LocalTime.NOON).atOffset(ZoneOffset.UTC);
    }

    public void refreshUnit() {

        // reinit
        hasUnsavedModifications = false;
        errorMessage = null;
        unit = null;

        try {

            unit = recordingUnitService.findById(idunit);

            specimenListLazyDataModel = new SpecimenInRecordingUnitLazyDataModel(
                    specimenService,
                    sessionSettingsBean,
                    langBean,
                    unit
            );
            specimenListLazyDataModel.setSelectedUnits(new ArrayList<>());

            backupClone = new RecordingUnit(unit);
            initForms();
            this.titleCodeOrTitle = unit.getFullIdentifier();

            specimenListLazyDataModel.setSelectedUnits(new ArrayList<>());

            // Get  the CHILDREN of the recording unit
            lazyDataModelChildren = new RecordingUnitChildrenLazyDataModel(
                    recordingUnitService,
                    langBean,
                    unit
            );
            selectedCategoriesChildren = new ArrayList<>();
            totalChildrenCount = 0;
            // Get all the Parents of the recording unit
            selectedCategoriesParents = new ArrayList<>();
            totalParentsCount = 0;
            lazyDataModelParents = new RecordingUnitParentsLazyDataModel(
                    recordingUnitService,
                    langBean,
                    unit
            );


        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load recording unit: " + e.getMessage();
        }


        history = historyAuditService.findAllRevisionForEntity(RecordingUnit.class, idunit);
        documents = documentService.findForRecordingUnit(unit);
    }

    @Override
    public void init() {
        try {

            if (idunit == null) {
                this.errorMessage = "The ID of the recording unit must be defined";
                return;
            }



            refreshUnit();

            if (this.unit == null) {
                log.error("The Recording Unit page should not be accessed without ID or by direct page path");
                errorMessage = "The Recording Unit page should not be accessed without ID or by direct page path";
            }

            super.init();

            SpecimenTab specimenTab = new SpecimenTab(
                    "common.entity.specimen",
                    "bi bi-bucket",
                    "specimenTab",
                    specimenListLazyDataModel,
                    0);

            tabs.add(specimenTab);


        } catch (
                ActionUnitNotFoundException e) {
            log.error("Recording unit with id {} not found", idunit);
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
        } catch (
                RuntimeException e) {
            this.errorMessage = "Failed to load recording unit: " + e.getMessage();
            redirectBean.redirectTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }


    }

    @Override
    public List<Person> authorsAvailable() {
        return List.of();
    }


    @Override
    public void initForms() {
        overviewForm = RecordingUnit.OVERVIEW_FORM;
        detailsForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(unit.getType(), sessionSettingsBean.getSelectedInstitution());
        // Init system form answers
        formResponse = initializeFormResponse(detailsForm, unit);
    }

    @Override
    public void cancelChanges() {
        unit.setSpatialUnit(backupClone.getSpatialUnit());
        unit.setThirdType(backupClone.getThirdType());
        unit.setSecondaryType(backupClone.getSecondaryType());
        unit.setArk(backupClone.getArk());
        unit.setType(backupClone.getType());
        unit.setStartDate(backupClone.getStartDate());
        unit.setEndDate(backupClone.getEndDate());
        unit.setAuthors(backupClone.getAuthors());
        unit.setExcavators(backupClone.getExcavators());
        hasUnsavedModifications = false;
        initForms();
    }

    @Override
    public void visualise(RevisionWithInfo<RecordingUnit> history) {
        // todo: implement
    }

    @Override
    protected boolean documentExistsInUnitByHash(RecordingUnit unit, String hash) {
        return documentService.existInRecordingUnitByHash(unit, hash);
    }

    @Override
    protected void addDocumentToUnit(Document doc, RecordingUnit unit) {
        documentService.addToRecordingUnit(doc, unit);
    }

    @Override
    public String getAutocompleteClass() {
        return "recording-unit-autocomplete";
    }

    @Override
    public boolean save(Boolean validated) {

        updateJpaEntityFromFormResponse(formResponse, unit);
        unit.setValidated(validated);
        if(Boolean.TRUE.equals(validated)) {
            unit.setValidatedBy(sessionSettingsBean.getAuthenticatedUser());
            unit.setValidatedAt(OffsetDateTime.now());
        }
        else {
            unit.setValidatedBy(null);
            unit.setValidatedAt(null);
        }

        try {
            recordingUnitService.save(unit, unit.getType(), List.of(), List.of(), List.of());
        } catch (FailedRecordingUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", unit.getFullIdentifier());
            return false;
        }

        refreshUnit();
        MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.updated", unit.getFullIdentifier());
        return true;
    }

    public static class RecordingUnitPanelBuilder {

        private final RecordingUnitPanel recordingUnitPanel;

        public RecordingUnitPanelBuilder(ObjectProvider<RecordingUnitPanel> recordingUnitPanelProvider) {
            this.recordingUnitPanel = recordingUnitPanelProvider.getObject();
        }

        public RecordingUnitPanel.RecordingUnitPanelBuilder id(Long id) {
            recordingUnitPanel.setIdunit(id);
            return this;
        }

        public RecordingUnitPanel.RecordingUnitPanelBuilder breadcrumb(PanelBreadcrumb breadcrumb) {
            recordingUnitPanel.setBreadcrumb(breadcrumb);

            return this;
        }

        public RecordingUnitPanel.RecordingUnitPanelBuilder tabIndex(Integer tabIndex) {
            recordingUnitPanel.setActiveTabIndex(tabIndex);

            return this;
        }


        public RecordingUnitPanel build() {
            recordingUnitPanel.init();
            return recordingUnitPanel;
        }
    }


}
