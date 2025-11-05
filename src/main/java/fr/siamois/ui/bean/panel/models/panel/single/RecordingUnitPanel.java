package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.actionunit.ActionUnitFormMapping;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectMultiple;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
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
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.*;
import java.util.*;

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

    protected RecordingUnitPanel(ApplicationContext context)  {

        super("common.entity.recordingunit",
                "bi bi-pencil-square",
                "siamois-panel recording-unit-panel single-panel",
                context);
        this.langBean = context.getBean(LangBean.class);
        this.recordingUnitService = context.getBean(RecordingUnitService.class);
        this.personService = context.getBean(PersonService.class);
        this.redirectBean = context.getBean(RedirectBean.class);
        this.specimenService = context.getBean(SpecimenService.class);
        this.formService = context.getBean(FormService.class);
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

    @Override
    protected String getFormScopePropertyName() {
        return "type";
    }

    @Override
    protected void setFormScopePropertyValue(Concept concept) {
        unit.setType(concept);
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
            initForms(true);
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
    public void initForms(boolean forceInit) {
        overviewForm = RecordingUnit.OVERVIEW_FORM;
        detailsForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(unit.getType(), sessionSettingsBean.getSelectedInstitution());
        // Init system form answers
        formResponse = initializeFormResponse(detailsForm, unit, forceInit);
        initEnabledRulesFromForms();
    }

    @Override
    public void cancelChanges() {
        unit.setSpatialUnit(backupClone.getSpatialUnit());
        unit.setThirdType(backupClone.getThirdType());
        unit.setSecondaryType(backupClone.getSecondaryType());
        unit.setArk(backupClone.getArk());
        unit.setIdentifier(backupClone.getIdentifier());
        unit.setType(backupClone.getType());
        unit.setOpeningDate(backupClone.getOpeningDate());
        unit.setClosingDate(backupClone.getClosingDate());
        unit.setAuthor(backupClone.getAuthor());
        unit.setCreatedBy(unit.getCreatedBy());
        unit.setContributors(backupClone.getContributors());
        unit.setGeomorphologicalCycle(backupClone.getGeomorphologicalCycle());
        unit.setNormalizedInterpretation(backupClone.getNormalizedInterpretation());
        hasUnsavedModifications = false;
        initForms(true);
    }

    @Override
    protected void initializeSystemField(CustomFieldAnswer answer, CustomField field) {

        // Recording unit identifier
        if(Objects.equals(field.getValueBinding(), "identifier") && field.getClass().equals(CustomFieldInteger.class)) {
            ((CustomFieldInteger) field).setMaxValue(unit.getActionUnit().getMaxRecordingUnitCode());
            ((CustomFieldInteger) field).setMinValue(unit.getActionUnit().getMinRecordingUnitCode());
        }
        // Min and max datetime
        if(field.getClass().equals(CustomFieldDateTime.class)) {
            if(Objects.equals(field.getValueBinding(), "openingDate") && unit.getClosingDate() != null) {
                ((CustomFieldDateTime) field).setMax(unit.getClosingDate().toLocalDateTime());
                ((CustomFieldDateTime) field).setMin(LocalDateTime.of(1000,Month.JANUARY,1, 1, 1));
            };
            if(Objects.equals(field.getValueBinding(), "closingDate") && unit.getOpeningDate() != null) {
                ((CustomFieldDateTime) field).setMin(unit.getOpeningDate().toLocalDateTime());
                ((CustomFieldDateTime) field).setMax(LocalDateTime.of(9999,Month.DECEMBER,31, 23, 59));
            };
        }

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
