package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customform.CustomFormComposer;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.form.RecordingUnitDetailsForm;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.form.EffectiveFormResolver;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.infrastructure.database.repositories.specs.SpecimenSpec;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.dialog.duplicate.DuplicateStructureDialogBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.bean.panel.models.panel.single.tab.MultiHierarchyTab;
import fr.siamois.ui.bean.panel.models.panel.single.tab.SpecimenTab;
import fr.siamois.ui.bean.panel.models.panel.single.tab.StratigraphyTab;
import fr.siamois.ui.form.dto.CustomColUiDto;
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
    private final transient DuplicateStructureDialogBean duplicateStructureDialogBean;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient EffectiveFormResolver effectiveFormResolver;


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
        this.duplicateStructureDialogBean = context.getBean(DuplicateStructureDialogBean.class);
        this.profilePermissionService = context.getBean(ProfilePermissionService.class);
        this.effectiveFormResolver = context.getBean(EffectiveFormResolver.class);

    }


    public String entityRessourceUri() {
        return "/recording-unit/" + unitId;
    }

    @Override
    public boolean canUserEditUnit() {
        return unit != null && profilePermissionService.hasRecordingUnitWritePermission(sessionSettingsBean.getUserInfo(), unit);
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
        if (!profilePermissionService.hasRecordingUnitWritePermission(sessionSettingsBean.getUserInfo(), unit)) {
            MessageUtils.displayWarnMessage(langBean, "common.error.forbidden");
            return;
        }

        RecordingUnitDTO copy = new RecordingUnitDTO(unit);
        copy.setParents(new HashSet<>());
        copy.setAuthor(sessionSettingsBean.getAuthenticatedUser());
        copy.setCreatedBy(sessionSettingsBean.getAuthenticatedUser());

        RecordingUnitDTO saved = recordingUnitService.save(copy);
        saved.setFullIdentifier(recordingUnitService.generateFullIdentifier(saved.getActionUnit(), saved));
        if (recordingUnitService.fullIdentifierAlreadyExistInAction(saved)) {
            MessageUtils.displayWarnMessage(langBean, "recordingunit.error.identifier.alreadyExists");
            throw new IllegalStateException("Generated recording-unit identifier already exists");
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

        try {

            unit = recordingUnitService.findById(unitId);


            SpecimenLazyDataModel specimenListLazyDataModel = new SpecimenLazyDataModel(specimenService, sessionSettingsBean, langBean);
            specimenListLazyDataModel.withConstantFilter(SpecimenSpec.RECORDING_UNIT_FILTER, List.of(unit.getId()), FilterDTO.FilterType.CONTAINS);
            specimenListLazyDataModel.setSelectedUnits(new ArrayList<>());


            initForms(true);
            this.titleCodeOrTitle = unit.getFullIdentifier();

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
        documents = unit != null ? documentService.findForRecordingUnit(unit) : List.of();
    }

    @Override
    protected String currentIdentifierValue() {
        return unit.getFullIdentifier();
    }

    @Override
    protected boolean persistIdentifierEdit(String trimmed) {
        if (trimmed.isEmpty()) {
            MessageUtils.displayWarnMessage(langBean, "recordingunit.error.identifier.blank");
            return false;
        }

        String previous = unit.getFullIdentifier();
        unit.setFullIdentifier(trimmed);

        if (recordingUnitService.fullIdentifierAlreadyExistInAction(unit)) {
            unit.setFullIdentifier(previous);
            MessageUtils.displayWarnMessage(langBean, "recordingunit.error.identifier.alreadyExists");
            return false;
        }

        try {
            recordingUnitService.save(unit);
            this.titleCodeOrTitle = unit.getFullIdentifier();
            return true;
        } catch (FailedRecordingUnitSaveException e) {
            unit.setFullIdentifier(previous);
            MessageUtils.displayErrorMessage(sessionSettingsBean.getLangBean(), "common.entity.recordingUnits.updateFailed", unit.getFullIdentifier());
            return false;
        }
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

            if (!profilePermissionService.canViewRecordingUnit(sessionSettingsBean.getUserInfo().getUser(), unit)) {
                log.warn("Person {} tried to access recording unit {} without permission", sessionSettingsBean.getUserInfo().getUser(), unitId);
                redirectBean.redirectTo(HttpStatus.FORBIDDEN);
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
        flowBean.addRecordingUnitToOverview(id, parentOrOverview, activeTabIndex, false);
    }

    @Override
    protected RecordingUnitDTO findNext() {
        return recordingUnitService.findNextByActionUnit(unit.getActionUnit(), unit);
    }

    @Override
    protected RecordingUnitDTO findPrevious() {
        return recordingUnitService.findPreviousByActionUnit(unit.getActionUnit(), unit);
    }

    @Override
    protected void doToggleValidate() {
        unit = recordingUnitService.toggleValidated(unit.getId());
    }


    @Override
    public void initForms(boolean forceInit) {
        Long typeConceptId = unit.getType() != null ? unit.getType().getId() : null;
        FormUiDto base = effectiveFormResolver.resolveEffectiveForm(
                RecordingUnit.DETAILS_FORM, unit.getActionUnit().getId(), ConfigurableTable.UE, typeConceptId);
        detailsForm = CustomFormComposer.withFieldsInPanel(base,
                RecordingUnitDetailsForm.MEASUREMENTS_PANEL_NAME, measurementFields());
        configureSystemFieldsBeforeInit();
        // Init system form answers
        initFormContext(forceInit);
    }

    private List<CustomColUiDto> measurementFields() {
        return formContextServices.getCustomFieldMeasurementService()
                .findByRecordingUnit(unit.getId()).stream()
                .map(field -> new CustomColUiDto.Builder()
                        .className("ui-g-12 ui-md-6 ui-lg-6")
                        .field(field)
                        .build())
                .toList();
    }



    @Override
    protected void configureSystemFieldsBeforeInit() {

        for (CustomField field : getAllFieldsFrom(detailsForm)) {

            if (field instanceof CustomFieldDateTime dt) {
                if ("openingDate".equals(field.getValueBinding()) && unit.getClosingDate() != null) {
                    dt.setMax(unit.getClosingDate().toLocalDateTime());
                }
                if ("closingDate".equals(field.getValueBinding()) && unit.getOpeningDate() != null) {
                    dt.setMin(unit.getOpeningDate().toLocalDateTime());
                }
            }
        }
    }


    @Override
    public void visualise(RevisionWithInfo<RecordingUnitDTO> history) {
        // Not implemented yet
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
                profilePermissionService,
                recordingUnitService,
                langBean,
                formContextServices,
                effectiveFormResolver,
                duplicateStructureDialogBean
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
                        .createAllowedSupplier(() -> profilePermissionService.hasProjectPermission(
                                sessionSettingsBean.getUserInfo(), unit.getActionUnit().getId(), PermissionConstants.PROJECT_EDIT_RECORDING_UNITS))
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
                specimenService,
                profilePermissionService,
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
                        .createAllowedSupplier(() -> unit.getActionUnit() != null && profilePermissionService.hasProjectPermission(
                                sessionSettingsBean.getUserInfo(), unit.getActionUnit().getId(), PermissionConstants.PROJECT_EDIT_FINDS))
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
