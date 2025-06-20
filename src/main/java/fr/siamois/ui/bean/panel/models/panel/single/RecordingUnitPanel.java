package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.actionunit.ActionUnitFormMapping;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customfieldanswer.*;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.history.RecordingUnitHist;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.HistoryService;
import fr.siamois.domain.services.SpatialUnitService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.document.DocumentCreationBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.utils.MessageUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.menu.DefaultMenuItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RecordingUnitPanel extends AbstractSingleEntityPanel<RecordingUnit, RecordingUnitHist> {

    // Deps
    protected final transient LangBean langBean;
    protected final transient SessionSettingsBean sessionSettingsBean;
    protected final transient SpatialUnitService spatialUnitService;
    protected final transient ActionUnitService actionUnitService;
    protected final transient RecordingUnitService recordingUnitService;
    protected final transient PersonService personService;
    private final transient RedirectBean redirectBean;
    private final transient HistoryService historyService;
    private final transient DocumentService documentService;
    protected final transient ConceptService conceptService;
    protected final transient FieldConfigurationService fieldConfigurationService;

    // ---------- Locals
    // RU
    protected RecordingUnit recordingUnit;

    // Form
    protected CustomForm additionalForm;


    // ----------- Concepts for system fields
    // Recording unit identifier
    private Concept recordingUnitIdConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286193")
            .build();
    // Authors
    private Concept authorsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286194")
            .build();
    // Excavators
    private Concept excavatorsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286195")
            .build();

    // Recording Unit type
    private Concept recordingUnitTypeConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4282367")
            .build();
    private Concept recordingUnitSecondaryTypeConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286196")
            .build();
    private Concept recordingUnitIdentificationConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286197")
            .build();

    // Date
    private Concept creationDateConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286200")
            .build();
    private Concept openingDateConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286198")
            .build();
    private Concept closingDateConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286199")
            .build();

    // Action Unit
    private Concept actionUnitConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286244")
            .build();

    // Spatial Unit
    private Concept spatialUnitConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286245")
            .build();

    // Fields
    private CustomFieldText recordingUnitIdField = new CustomFieldText.Builder()
            .label("recordingunit.field.identifier")
            .isSystemField(true)
            .valueBinding("fullIdentifier")
            .concept(recordingUnitIdConcept)
            .build();

    private CustomFieldSelectMultiplePerson authorsField = new CustomFieldSelectMultiplePerson.Builder()
            .label("recordingunit.field.authors")
            .isSystemField(true)
            .valueBinding("authors")
            .concept(authorsConcept)
            .build();

    private CustomFieldSelectMultiplePerson excavatorsField = new CustomFieldSelectMultiplePerson.Builder()
            .label("recordingunit.field.excavators")
            .isSystemField(true)
            .valueBinding("excavators")
            .concept(excavatorsConcept)
            .build();

    private CustomFieldSelectOneFromFieldCode recordingUnitTypeField = new CustomFieldSelectOneFromFieldCode.Builder()
            .label("spatialunit.field.type")
            .isSystemField(true)
            .valueBinding("type")
            .styleClass("mr-2 recording-unit-type-chip")
            .iconClass("bi bi-pencil-square")
            .fieldCode(RecordingUnit.TYPE_FIELD_CODE)
            .concept(recordingUnitTypeConcept)
            .build();

    private CustomFieldSelectOneConceptFromChildrenOfConcept recordingUnitSecondaryTypeField = new CustomFieldSelectOneConceptFromChildrenOfConcept.Builder()
            .label("recordingunit.field.secondaryType")
            .isSystemField(true)
            .valueBinding("secondaryType")
            .styleClass("mr-2 recording-unit-type-chip")
            .iconClass("bi bi-pencil-square")
            .parentField(recordingUnitTypeField)
            .concept(recordingUnitSecondaryTypeConcept)
            .build();

    private CustomFieldSelectOneConceptFromChildrenOfConcept recordingUnitIdentificationField = new CustomFieldSelectOneConceptFromChildrenOfConcept.Builder()
            .label("recordingunit.field.thirdType")
            .isSystemField(true)
            .valueBinding("thirdType")
            .styleClass("mr-2 recording-unit-type-chip")
            .iconClass("bi bi-pencil-square")
            .parentField(recordingUnitSecondaryTypeField)
            .concept(recordingUnitIdentificationConcept)
            .build();

    private CustomFieldDateTime creationDateField = new CustomFieldDateTime.Builder()
            .label("recordingunit.field.creationDate")
            .isSystemField(true)
            .showTime(true)
            .valueBinding("creationTime")
            .concept(creationDateConcept)
            .build();




    private CustomFieldDateTime openingDateField = new CustomFieldDateTime.Builder()
            .label("recordingunit.field.openingDate")
            .isSystemField(true)
            .valueBinding("startDate")
            .showTime(false)
            .concept(openingDateConcept)
            .build();

    private CustomFieldDateTime closingDateField = new CustomFieldDateTime.Builder()
            .label("recordingunit.field.closingDate")
            .isSystemField(true)
            .valueBinding("endDate")
            .showTime(false)
            .concept(closingDateConcept)
            .build();

    private CustomFieldSelectOneActionUnit actionUnitField = new CustomFieldSelectOneActionUnit.Builder()
            .label("recordingunit.field.actionUnit")
            .isSystemField(true)
            .valueBinding("actionUnit")
            .concept(actionUnitConcept)
            .build();

    private CustomFieldSelectOneSpatialUnit spatialUnitField = new CustomFieldSelectOneSpatialUnit.Builder()
            .label("recordingunit.field.spatialUnit")
            .isSystemField(true)
            .valueBinding("spatialUnit")
            .concept(spatialUnitConcept)
            .build();


    // Details form
    private CustomForm detailsForm = new CustomForm.Builder()
            .name("Details tab form")
            .description("Contains the main form")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name("common.header.general")
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(recordingUnitIdField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(actionUnitField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(spatialUnitField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(authorsField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(excavatorsField)
                                                    .build())
                                            .build()
                            ).addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(recordingUnitTypeField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(recordingUnitSecondaryTypeField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(recordingUnitIdentificationField)
                                                    .build())
                                            .build()
                            ).addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(creationDateField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(openingDateField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(closingDateField)
                                                    .build())
                                            .build()
                            )
                            .build()
            )
            .build();

    // Details form
    private CustomForm overviewForm = new CustomForm.Builder()
            .name("Overview tab form")
            .description("Contains the overview")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name("common.header.general")
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(recordingUnitTypeField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(recordingUnitSecondaryTypeField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(recordingUnitIdentificationField)
                                                    .build())
                                            .build()
                            )
                            .build()
            )
            .build();


    protected RecordingUnitPanel(LangBean langBean,
                                 SessionSettingsBean sessionSettingsBean,
                                 SpatialUnitService spatialUnitService,
                                 ActionUnitService actionUnitService,
                                 RecordingUnitService recordingUnitService,
                                 PersonService personService, ConceptService conceptService,
                                 FieldConfigurationService fieldConfigurationService,
                                 DocumentCreationBean documentCreationBean,
                                 RedirectBean redirectBean,
                                 HistoryService historyService,
                                 DocumentService documentService) {

        super("common.entity.recordingunit",
                "bi bi-pencil-square",
                "siamois-panel recording-unit-panel recording-unit-single-panel",
                documentCreationBean, sessionSettingsBean, fieldConfigurationService);
        this.langBean = langBean;
        this.sessionSettingsBean = sessionSettingsBean;
        this.spatialUnitService = spatialUnitService;
        this.actionUnitService = actionUnitService;
        this.recordingUnitService = recordingUnitService;
        this.personService = personService;
        this.conceptService = conceptService;
        this.fieldConfigurationService = fieldConfigurationService;
        this.redirectBean = redirectBean;
        this.historyService = historyService;
        this.documentService = documentService;
    }

    @Override
    public String display() {
        return "/panel/recordingUnitPanel.xhtml";
    }

    @Override
    public String ressourceUri() {
        return "/recording-unit/" + idunit;
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

    public void handleSelectType() {

        if (recordingUnit.getType() != null) {

            changeCustomForm();
        } else {

        }

        recordingUnit.setSecondaryType(null);
        recordingUnit.setThirdType(null);


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

    @Override
    protected BaseLazyDataModel<RecordingUnit> getLazyDataModelChildren() {
        return null;
    }

    @Override
    public BaseLazyDataModel<RecordingUnit> getLazyDataModelParents() {
        return null;
    }

    public void refreshUnit() {

        // reinit
        hasUnsavedModifications = false;
        errorMessage = null;
        unit = null;

        try {

            unit = recordingUnitService.findById(idunit);
            backupClone = new RecordingUnit(unit);
            this.titleCodeOrTitle = unit.getFullIdentifier();

            initForms();

            // Get all the CHILDREN of the recording unit
            selectedCategoriesChildren = new ArrayList<>();
            totalChildrenCount = 0;
            // Get all the Parents of the recording unit
            selectedCategoriesParents = new ArrayList<>();
            totalParentsCount = 0;


        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load spatial unit: " + e.getMessage();
        }


        historyVersion = historyService.findRecordingUnitHistory(unit);
        documents = documentService.findForRecordingUnit(unit);
    }

    @Override
    public void init() {
        try {
            activeTabIndex = 0;


            if (idunit == null) {
                this.errorMessage = "The ID of the recording unit must be defined";
                return;
            }

            refreshUnit();

            if (this.unit == null) {
                log.error("The Action Unit page should not be accessed without ID or by direct page path");
                errorMessage = "The Action Unit page should not be accessed without ID or by direct page path";
            }

            // add to BC
            DefaultMenuItem item = DefaultMenuItem.builder()
                    .value(unit.getFullIdentifier())
                    .icon("bi bi-pencil-square")
                    .build();
            this.getBreadcrumb().getModel().getElements().add(item);

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

        // Init system form answers
        formResponse = initializeFormResponse(detailsForm, unit);

    }

    @Override
    public void cancelChanges() {

    }

    @Override
    public void visualise(RecordingUnitHist history) {

    }

    @Override
    public void saveDocument() {

    }

    @Override
    public String getAutocompleteClass() {
        return "recording-unit-autocomplete";
    }

    @Override
    public void save(Boolean validated) {

        updateJpaEntityFromFormResponse(formResponse, unit);
        unit.setValidated(validated);
        try {
            recordingUnitService.save(unit, unit.getType(), List.of(), List.of(), List.of());
        } catch (FailedRecordingUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.spatialUnits.updateFailed", unit.getFullIdentifier());
            return;
        }

        refreshUnit();
        MessageUtils.displayInfoMessage(langBean, "common.entity.spatialUnits.updated", unit.getFullIdentifier());

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


        public RecordingUnitPanel build() {
            recordingUnitPanel.init();
            return recordingUnitPanel;
        }
    }


}
