package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.actionunit.ActionUnitFormMapping;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectMultiple;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectMultiple;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.*;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.single.tab.SpecimenTab;
import fr.siamois.ui.form.FormUiDto;
import fr.siamois.ui.lazydatamodel.RecordingUnitChildrenLazyDataModel;
import fr.siamois.ui.lazydatamodel.RecordingUnitParentsLazyDataModel;
import fr.siamois.ui.lazydatamodel.SpecimenInRecordingUnitLazyDataModel;
import fr.siamois.ui.mapper.FormMapper;
import fr.siamois.ui.table.RecordingUnitTableViewModel;
import fr.siamois.ui.table.SpecimenTableViewModel;
import fr.siamois.ui.table.ToolbarCreateConfig;
import fr.siamois.ui.table.definitions.SpecimenTableDefinitionFactory;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.primefaces.model.menu.DefaultMenuItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RecordingUnitPanel extends AbstractSingleMultiHierarchicalEntityPanel<RecordingUnitDTO>  implements Serializable {


    protected final transient RecordingUnitService recordingUnitService;
    protected final transient PersonService personService;
    private final transient RedirectBean redirectBean;
    private final transient SpecimenService specimenService;
    private final transient NavBean navBean;
    private final transient GenericNewUnitDialogBean<?> genericNewUnitDialogBean;

    // ---------- Locals
    // Linked specimen
    private transient SpecimenInRecordingUnitLazyDataModel specimenListLazyDataModel ;

    // lazy model for children
    private RecordingUnitChildrenLazyDataModel lazyDataModelChildren ;
    // lazy model for parents
    private RecordingUnitParentsLazyDataModel lazyDataModelParents ;

    // lazy model for children
    private transient RecordingUnitTableViewModel parentTableModel;
    // lazy model for parents
    private transient RecordingUnitTableViewModel childTableModel;

    private transient SpecimenTableViewModel specimenTableModel;




    protected RecordingUnitPanel(ApplicationContext context)  {

        super("common.entity.recordingunit",
                "bi bi-pencil-square",
                "siamois-panel recording-unit-panel single-panel",
                context);
        this.recordingUnitService = context.getBean(RecordingUnitService.class);
        this.personService = context.getBean(PersonService.class);
        this.redirectBean = context.getBean(RedirectBean.class);
        this.specimenService = context.getBean(SpecimenService.class);
        this.navBean = context.getBean(NavBean.class);
        this.genericNewUnitDialogBean = context.getBean(GenericNewUnitDialogBean.class);

    }


    @Override
    public String ressourceUri() {
        return "/recording-unit/" + unitId;
    }

    @Override
    public String displayHeader() {
        return "/panel/header/recordingUnitPanelHeader.xhtml";
    }

    /**
     * Returns all the spatial units a recording unit can be attached to
     *
     * @return The list of spatial unit
     */
    @Override
    public List<SpatialUnitSummaryDTO> getSpatialUnitOptions() {

        if(unit == null) return Collections.emptyList();
        return spatialUnitService.getSpatialUnitOptionsFor(unit);
    }


    @Override
    protected String getFormScopePropertyName() {
        return "type";
    }

    @Override
    protected void setFormScopePropertyValue(ConceptDTO concept) {
        unit.setType(concept);
    }


    public void refreshUnit() {

        // reinit
        errorMessage = null;
        unit = null;

        try {

            unit = recordingUnitService.findById(unitId);


            specimenListLazyDataModel = new SpecimenInRecordingUnitLazyDataModel(
                    specimenService,
                    sessionSettingsBean,
                    langBean,
                    unit
            );
            specimenListLazyDataModel.setSelectedUnits(new ArrayList<>());


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


        //history = historyAuditService.findAllRevisionForEntity(RecordingUnitDTO.class, unitId);
        documents = documentService.findForRecordingUnit(unit);
    }

    @Override
    List<RecordingUnitDTO> findDirectParentsOf(Long id) {
        return recordingUnitService.findDirectParentsOf(id);
    }

    @Override
    RecordingUnitDTO findUnitById(Long id) {
        return recordingUnitService.findById(id);
    }

    @Override
    String findLabel(RecordingUnitDTO unit) {
        return unit.getFullIdentifier();
    }

    @Override
    String getOpenPanelCommand(RecordingUnitDTO unit) {
        return "#{flowBean.addRecordingUnitPanel(".concat(unit.getId().toString()).concat(")}");
    }

    @Override
    protected DefaultMenuItem createRootTypeItem()
    {
        return DefaultMenuItem.builder()
                .value(langBean.msg("panel.title.allrecordingunit"))
                .id("allRecordingUnits")
                .command("#{flowBean.addRecordingUnitListPanel()}")
                .update("flow")
                .onstart(PF_BUI_CONTENT_SHOW)
                .oncomplete(PF_BUI_CONTENT_HIDE)
                .process(THIS)
                .build();
    }


    @Override
    public void init() {
        try {

            if (unitId == null) {
                this.errorMessage = "The ID of the recording unit must be defined";
                return;
            }



            refreshUnit();

            if (this.unit == null) {
                log.error("The Recording Unit page should not be accessed without ID or by direct page path");
                errorMessage = "The Recording Unit page should not be accessed without ID or by direct page path";
            }

            initSpecimenTab();

            super.init();

            SpecimenTab specimenTab = new SpecimenTab(
                    "common.entity.specimen",
                    "bi bi-bucket",
                    "specimenTab",
                    specimenTableModel,
                    0);

            tabs.add(specimenTab);


        } catch (
                ActionUnitNotFoundException e) {
            log.error("Recording unit with id {} not found", unitId);
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
        } catch (
                RuntimeException e) {
            this.errorMessage = "Failed to load recording unit: " + e.getMessage();
            redirectBean.redirectTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    @Override
    public List<PersonDTO> authorsAvailable() {
        return List.of();
    }


    @Override
    public void initForms(boolean forceInit) {
        CustomForm form = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(unit.getType(), sessionSettingsBean.getSelectedInstitution());
        detailsForm = formContextServices.getConversionService().convert(form, FormUiDto.class);
        configureSystemFieldsBeforeInit();
        // Init system form answers
        initFormContext(forceInit);
    }



    @Override
    protected void configureSystemFieldsBeforeInit() {

        for (CustomField field : getAllFieldsFrom(detailsForm)) {

            if ("identifier".equals(field.getValueBinding()) && field instanceof CustomFieldInteger cfi) {
                cfi.setMaxValue(unit.getActionUnit().getMaxRecordingUnitCode());
                cfi.setMinValue(unit.getActionUnit().getMinRecordingUnitCode());
            }

            if (field instanceof CustomFieldDateTime dt) {
                if ("openingDate".equals(field.getValueBinding()) && unit.getClosingDate() != null) {
                    dt.setMax(unit.getClosingDate().toLocalDateTime());
                    dt.setMin(LocalDateTime.of(1000, 1, 1, 1, 1));
                }
                if ("closingDate".equals(field.getValueBinding()) && unit.getOpeningDate() != null) {
                    dt.setMin(unit.getOpeningDate().toLocalDateTime());
                    dt.setMax(LocalDateTime.of(9999, 12, 31, 23, 59));
                }
            }
        }
    }


    @Override
    public void visualise(RevisionWithInfo<RecordingUnitDTO> history) {
        // todo: implement
    }

    @Override
    protected boolean documentExistsInUnitByHash(RecordingUnitDTO unit, String hash) {
        return documentService.existInRecordingUnitByHash(unit, hash);
    }

    @Override
    protected void addDocumentToUnit(Document doc, RecordingUnitDTO unit) {
        documentService.addToRecordingUnit(doc, unit);
    }

    @Override
    public String getAutocompleteClass() {
        return "recording-unit-autocomplete";
    }



    @Override
    public boolean save(Boolean validated) {
        return formContext.save();
        // update bandeau?
        // update bc?
    }

    public static class RecordingUnitPanelBuilder {

        private final RecordingUnitPanel recordingUnitPanel;

        public RecordingUnitPanelBuilder(ObjectProvider<RecordingUnitPanel> recordingUnitPanelProvider) {
            this.recordingUnitPanel = recordingUnitPanelProvider.getObject();
        }

        public RecordingUnitPanel.RecordingUnitPanelBuilder id(Long id) {
            recordingUnitPanel.setUnitId(id);
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

    @Override
    public String getTabView() {
        return "/panel/tabview/recordingUnitTabView.xhtml";
    }

    public void initSpecimenTab() {
        SpecimenInRecordingUnitLazyDataModel lazyDataModel = new SpecimenInRecordingUnitLazyDataModel(
                specimenService,
                sessionSettingsBean,
                langBean,
                unit
        );

        specimenTableModel = new SpecimenTableViewModel(
                lazyDataModel,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                flowBean,
                (GenericNewUnitDialogBean<SpecimenDTO>) genericNewUnitDialogBean,
                formContextServices
        );

        SpecimenTableDefinitionFactory.applyTo(specimenTableModel);

        // configuration du bouton creer
        specimenTableModel.setToolbarCreateConfig(
                ToolbarCreateConfig.builder()
                        .kindToCreate(UnitKind.SPECIMEN)
                        .scopeSupplier(() ->
                                NewUnitContext.Scope.builder()
                                        .key("RECORDING")
                                        .entityId(unit.getId())
                                        .build()
                        )
                        .build()
        );
    }

    @Override
    public String getPanelIndex() {
        return "recording-unit-"+ unitId;
    }

    @Override
    public String getPanelTypeClass() {
        return "recording-unit";
    }

}
