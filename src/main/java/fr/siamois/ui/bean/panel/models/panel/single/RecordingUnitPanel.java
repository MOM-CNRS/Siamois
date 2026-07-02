package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.CustomFieldInteger;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.services.authorization.writeverifier.RecordingUnitWriteVerifier;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.SpecimenSpec;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.bean.panel.models.panel.single.tab.MultiHierarchyTab;
import fr.siamois.ui.bean.panel.models.panel.single.tab.SpecimenTab;
import fr.siamois.ui.bean.panel.models.panel.single.tab.StratigraphyTab;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.lazydatamodel.RecordingUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.SpecimenLazyDataModel;
import fr.siamois.ui.table.ToolbarCreateConfig;
import fr.siamois.ui.table.definitions.RecordingUnitTableDefinitionFactory;
import fr.siamois.ui.table.definitions.SpecimenTableDefinitionFactory;
import fr.siamois.ui.table.viewmodel.RecordingUnitTableViewModel;
import fr.siamois.ui.table.viewmodel.SpecimenTableViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerStratigraphyViewModel;
import fr.siamois.utils.MessageUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.menu.DefaultMenuItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

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
    private final transient RecordingUnitWriteVerifier recordingUnitWriteVerifier;


    // lazy model for children
    private transient RecordingUnitTableViewModel parentTableModel;
    // lazy model for parents
    private transient RecordingUnitTableViewModel childTableModel;

    private transient SpecimenTableViewModel specimenTableModel;

    // Strati
    private CustomFieldAnswerStratigraphyViewModel stratigraphyViewModel;




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
        this.recordingUnitWriteVerifier = context.getBean(RecordingUnitWriteVerifier.class);

    }


    public String entityRessourceUri() {
        return "/recording-unit/" + unitId;
    }

    @Override
    public String displayHeader() {
        return "/panel/header/recordingUnitPanelHeader.xhtml";
    }

    @Override
    public UnitKind getCreationUnitKind() {
        return UnitKind.RECORDING;
    }

    @Override
    public NewUnitContext buildCreationContext(UnitKind kind) {
        if (unit == null || unit.getActionUnit() == null) return super.buildCreationContext(kind);
        return NewUnitContext.builder()
                .kindToCreate(kind)
                .trigger(NewUnitContext.Trigger.toolbar())
                .scope(NewUnitContext.Scope.linkedTo("ACTION", unit.getActionUnit().getId()))
                .build();
    }

    @Override
    public boolean canDuplicate() {
        return true;
    }

    @Override
    public void duplicate() {
        RecordingUnitDTO copy = new RecordingUnitDTO(unit);
        copy.setParents(new HashSet<>());
        copy.setAuthor(sessionSettingsBean.getAuthenticatedUser());
        copy.setCreatedBy(sessionSettingsBean.getAuthenticatedUser());

        RecordingUnitDTO saved = recordingUnitService.save(copy);
        saved.setFullIdentifier(recordingUnitService.generateFullIdentifier(saved.getActionUnit(), saved));
        if (recordingUnitService.fullIdentifierAlreadyExistInAction(saved)) {
            saved.setFullIdentifier(saved.getActionUnit().getRecordingUnitIdentifierFormat());
            MessageUtils.displayWarnMessage(langBean, "recordingunit.error.identifier.alreadyExists");
        }
        saved = recordingUnitService.save(saved);

        flowBean.addRecordingUnitToOverview(saved.getId(), this, null);
        MessageUtils.displayInfoMessage(langBean, "common.action.duplicateEntity", unit.getFullIdentifier());
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

        // TODO PERF (temporaire) : chronométrage des blocs de refreshUnit. À retirer.
        long tStart = System.nanoTime();
        long tFindById = tStart;
        long tForms = tStart;
        long tTabs = tStart;
        try {

            unit = recordingUnitService.findById(unitId);
            tFindById = System.nanoTime();


            SpecimenLazyDataModel specimenListLazyDataModel = new SpecimenLazyDataModel(specimenService, sessionSettingsBean, langBean);
            specimenListLazyDataModel.withConstantFilter(SpecimenSpec.RECORDING_UNIT_FILTER, List.of(unit.getId()), FilterDTO.FilterType.CONTAINS);
            specimenListLazyDataModel.setSelectedUnits(new ArrayList<>());


            initForms(true);
            this.titleCodeOrTitle = unit.getFullIdentifier();
            tForms = System.nanoTime();

            specimenListLazyDataModel.setSelectedUnits(new ArrayList<>());

            // Get  the CHILDREN of the recording unit
            RecordingUnitLazyDataModel lazyDataModelChildren = new RecordingUnitLazyDataModel(recordingUnitService, sessionSettingsBean, langBean);
            lazyDataModelChildren.withConstantFilter(RecordingUnitSpec.PARENTS_FILTER, List.of(unit.getId()), FilterDTO.FilterType.CONTAINS);
            selectedCategoriesChildren = new ArrayList<>();
            totalChildrenCount = 0;
            // Get all the Parents of the recording unit
            selectedCategoriesParents = new ArrayList<>();
            totalParentsCount = 0;

            initChildTableModelForHierarchyTab(lazyDataModelChildren);

            // iniy stratigraphy module
            stratigraphyViewModel = new CustomFieldAnswerStratigraphyViewModel();
            formService.handleStratigraphyRelationships(stratigraphyViewModel, unit);
            tTabs = System.nanoTime();
            // --Define callbacks
            stratigraphyViewModel.setOnDelete(() -> {
                formService.setStratigraphyFieldValue(stratigraphyViewModel, unit);
                recordingUnitService.updateStratigraphicRel(unit);
            });


            stratigraphyViewModel.setOnAdd((context, cc) -> {
                formContext.addStratigraphicRelationship(stratigraphyViewModel, context, cc);
                // update rels and save
                formService.setStratigraphyFieldValue(stratigraphyViewModel, unit);
                recordingUnitService.updateStratigraphicRel(unit);
            });


        } catch (RecordingUnitNotFoundException e) {
            log.warn("Recording unit id={} not found when loading panel", unitId);
            this.errorMessage = langBean.msg("recordingunit.panel.notFound", String.valueOf(unitId));
        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load recording unit: " + e.getMessage();
        }

        //history = historyAuditService.findAllRevisionForEntity(RecordingUnitDTO.class, unitId);
        long tBeforeDocs = System.nanoTime();
        documents = unit != null ? documentService.findForRecordingUnit(unit) : List.of();
        long tEnd = System.nanoTime();
        log.debug("⏱ refreshUnit[ru={}] findById={}ms forms={}ms tabsModels={}ms documents={}ms (total {}ms)",
                unitId,
                (tFindById - tStart) / 1_000_000,
                (tForms - tFindById) / 1_000_000,
                (tTabs - tForms) / 1_000_000,
                (tEnd - tBeforeDocs) / 1_000_000,
                (tEnd - tStart) / 1_000_000);
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
    protected DefaultMenuItem createRootTypeItem()
    {
        if (unit == null || unit.getActionUnit() == null) {
            return DefaultMenuItem.builder()
                    .value("")
                    .id("actionUnit")
                    .disabled(true)
                    .build();
        }

        String command ;
        Long actionUnitId = unit.getActionUnit().getId();
        if(isRoot) {
            command = "#{navBean.redirectToBookmarked('/action-unit/"+actionUnitId+"')}";
        } else {
            command = "#{flowBean.addActionUnitToOverview(" + actionUnitId + ", focusViewBean.mainPanel, 2)}";
        }

        return DefaultMenuItem.builder()
                .value(unit.getActionUnit().getName())
                .id("actionUnit")
                .command(command)
                .icon("bi bi-arrow-down-square")
                .update("@this")
                .onstart(PF_BUI_CONTENT_SHOW)
                .oncomplete(PF_BUI_CONTENT_HIDE)
                .process(THIS)
                .build();
    }


    @Override
    public void loadData() {
        super.loadData();
        ensureTabsInitialized();
    }

    public boolean isTabViewReady() {
        return unit != null && tabs != null && tabs.size() >= 5;
    }

    private void ensureTabsInitialized() {
        if (unit == null || isTabViewReady()) {
            return;
        }

        initSpecimenTab();

        while (tabs.size() > 2) {
            tabs.remove(2);
        }

        tabs.add(2, new MultiHierarchyTab("panel.tab.hierarchy", getIcon(), "hierarchyTab", getChildTableModel()));

        tabs.add(new SpecimenTab("common.entity.specimen", "bi bi-bucket", "specimenTab", specimenTableModel, 0));

        tabs.add(new StratigraphyTab("common.label.ruRelationships", "bi bi-diagram-2", "stratiTab"));
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
                if (errorMessage == null || errorMessage.isBlank()) {
                    errorMessage = langBean.msg("recordingunit.panel.notFound", String.valueOf(unitId));
                }
                log.error("Recording unit panel opened without loadable unit for unitId={}", unitId);
                return;
            }

            ensureTabsInitialized();


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
    protected String getFocusPath(Long id) {
        return "/recording-unit/"+id;
    }

    @Override
    protected void addToOverview(Long id, AbstractPanel parentOrOverview, Integer activeTabIndex) {
        flowBean.addRecordingUnitToOverview(id,parentOrOverview, activeTabIndex);
    }

    @Override
    protected Long findNextId() {
        return recordingUnitService.findNextIdByActionUnit(unit.getActionUnit(), unit);
    }

    @Override
    protected Long findPreviousId() {
        return recordingUnitService.findPreviousIdByActionUnit(unit.getActionUnit(), unit);
    }

    @Override
    public void toggleValidate() {
        unit = recordingUnitService.toggleValidated(unit.getId());
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
                    dt.setMin(LocalDateTime.of(1000, Month.JANUARY, 1, 1, 1));
                }
                if ("closingDate".equals(field.getValueBinding()) && unit.getOpeningDate() != null) {
                    dt.setMin(unit.getOpeningDate().toLocalDateTime());
                    dt.setMax(LocalDateTime.of(9999, Month.DECEMBER, 31, 23, 59));
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

    private void initChildTableModelForHierarchyTab(RecordingUnitLazyDataModel lazyDataModelChildren) {
        if (unit == null) {
            return;
        }
        childTableModel = new RecordingUnitTableViewModel(
                lazyDataModelChildren,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                flowBean,
                (GenericNewUnitDialogBean<RecordingUnitDTO>) genericNewUnitDialogBean,
                recordingUnitWriteVerifier,
                recordingUnitService,
                langBean,
                formContextServices
        );
        childTableModel.setParentPanel(this);
        RecordingUnitTableDefinitionFactory.applyTo(childTableModel);
        childTableModel.setToolbarCreateConfig(
                ToolbarCreateConfig.builder()
                        .kindToCreate(UnitKind.RECORDING)
                        .scopeSupplier(() ->
                                NewUnitContext.Scope.builder()
                                        .key("ACTION")
                                        .entityId(unit.getActionUnit().getId())
                                        .build())
                        .build());
    }

    public void initSpecimenTab() {
        SpecimenLazyDataModel lazyDataModel = new SpecimenLazyDataModel(specimenService, sessionSettingsBean, langBean);
        lazyDataModel.withConstantFilter(SpecimenSpec.RECORDING_UNIT_FILTER, List.of(unit.getId()), FilterDTO.FilterType.CONTAINS);

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
        specimenTableModel.setParentPanel(this);
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
    public String getPrefixPanelIndex() {
        return "recording-unit-"+ unitId;
    }

    @Override
    public String svgIcon() {
        return "/resources/img/svg/pencil-square.svg";
    }

    @Override
    public String getPanelTypeClass() {
        return "recording-unit";
    }

}
