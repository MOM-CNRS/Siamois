package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.writeverifier.SpatialUnitWriteVerifier;
import fr.siamois.domain.services.form.CustomFieldService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.single.tab.ActionTab;
import fr.siamois.ui.bean.panel.utils.SpatialUnitHelperService;
import fr.siamois.ui.lazydatamodel.*;
import fr.siamois.ui.lazydatamodel.tree.ActionUnitTreeTableLazyModel;
import fr.siamois.ui.lazydatamodel.tree.SpatialUnitTreeTableLazyModel;
import fr.siamois.ui.table.ActionUnitTableViewModel;
import fr.siamois.ui.table.SpatialUnitTableViewModel;
import fr.siamois.ui.table.ToolbarCreateConfig;
import fr.siamois.ui.table.definitions.ActionUnitTableDefinitionFactory;
import fr.siamois.ui.table.definitions.SpatialUnitTableDefinitionFactory;
import fr.siamois.utils.MessageUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static fr.siamois.ui.lazydatamodel.ActionUnitScope.Type.LINKED_TO_SPATIAL_UNIT;
import static fr.siamois.ui.lazydatamodel.SpatialUnitScope.Type.CHILDREN_OF_SPATIAL_UNIT;
import static fr.siamois.ui.lazydatamodel.SpatialUnitScope.Type.PARENTS_OF_SPATIAL_UNIT;

/**
 * <p>This bean handles the spatial unit page</p>
 *
 * @author Grégory Bliault
 */
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Slf4j
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SpatialUnitPanel extends AbstractSingleMultiHierarchicalEntityPanel<SpatialUnit> implements Serializable {

    // Dependencies
    private final transient RecordingUnitService recordingUnitService;
    private final transient SpecimenService specimenService;
    private final transient SessionSettingsBean sessionSettings;
    private final transient SpatialUnitHelperService spatialUnitHelperService;
    private final transient CustomFieldService customFieldService;
    private final transient LabelService labelService;
    private final transient LangBean langBean;
    private final transient PersonService personService;
    private final transient NavBean navBean;
    private final transient FlowBean flowBean;
    private final transient GenericNewUnitDialogBean<?> genericNewUnitDialogBean;
    private final transient InstitutionService institutionService;
    private final transient SpatialUnitWriteVerifier spatialUnitWriteVerifier;


    private String spatialUnitErrorMessage;
    private transient List<SpatialUnit> spatialUnitList;
    private transient List<SpatialUnit> spatialUnitParentsList;
    private String spatialUnitListErrorMessage;
    private String spatialUnitParentsListErrorMessage;

    // lazy model for children
    private transient SpatialUnitTableViewModel parentTableModel;
    // lazy model for parents
    private transient SpatialUnitTableViewModel childTableModel;


    // Lazy  for actions in the spatial unit
    private transient ActionUnitTableViewModel actionTabTableModel;
    private Integer totalActionUnitCount;



    @Autowired
    private SpatialUnitPanel(ApplicationContext context) {
        super("common.entity.spatialUnit", "bi bi-geo-alt", "siamois-panel spatial-unit-panel single-panel", context);
        this.recordingUnitService = context.getBean(RecordingUnitService.class);
        this.sessionSettings = context.getBean(SessionSettingsBean.class);
        this.spatialUnitHelperService = context.getBean(SpatialUnitHelperService.class);
        this.customFieldService = context.getBean(CustomFieldService.class);
        this.labelService = context.getBean(LabelService.class);
        this.langBean = context.getBean(LangBean.class);
        this.personService = context.getBean(PersonService.class);
        this.specimenService = context.getBean(SpecimenService.class);
        this.navBean = context.getBean(NavBean.class);
        this.flowBean = context.getBean(FlowBean.class);
        this.genericNewUnitDialogBean = context.getBean(GenericNewUnitDialogBean.class);
        this.institutionService = context.getBean(InstitutionService.class);
        this.spatialUnitWriteVerifier = context.getBean(SpatialUnitWriteVerifier.class);
    }


    public List<ConceptLabel> categoriesAvailable() {
        List<Concept> cList = conceptService.findAllBySpatialUnitOfInstitution(sessionSettings.getSelectedInstitution());
        return new ArrayList<>(cList.stream()
                .map(concept -> labelService.findLabelOf(
                        concept, langBean.getLanguageCode()
                ))
                .toList());

    }

    @Override
    public String getAutocompleteClass() {
        return "spatial-unit-autocomplete";
    }


    @Override
    public String ressourceUri() {
        return "/spatial-unit/" + idunit;
    }

    @Override
    public String displayHeader() {
        return "/panel/header/spatialUnitPanelHeader.xhtml";
    }




    @Override
    public List<Person> authorsAvailable() {

        return personService.findAllAuthorsOfSpatialUnitByInstitution(sessionSettings.getSelectedInstitution());

    }

    @Override
    public void initForms(boolean forceInit) {

        detailsForm = SpatialUnit.DETAILS_FORM;
        // Init system form answers
        initFormContext(forceInit);
    }

    @Override
    protected String getFormScopePropertyName() {
        return "";
    }

    @Override
    protected void setFormScopePropertyValue(Concept concept) {
        unit.setCategory(concept);
    }

    public void refreshUnit() {

        unit = null;
        spatialUnitHelperService.reinitialize(
                unit -> this.unit = unit,
                msg -> this.spatialUnitErrorMessage = msg,
                msg -> this.spatialUnitListErrorMessage = msg,
                list -> this.spatialUnitList = list,
                list -> this.spatialUnitParentsList = list,
                msg -> this.spatialUnitParentsListErrorMessage = msg
        );

        try {

            this.unit = spatialUnitService.findById(idunit);
            this.setTitleCodeOrTitle(unit.getName()); // Set panel title

            backupClone = new SpatialUnit(unit);

            initForms(true);

            // ---------  Action Tab
            initActionTab();

            // hierarchy tabs
            initChildTableForHierarchyTab();
            initParentTableForHierarchyTab();


        } catch (RuntimeException e) {
            this.spatialUnitErrorMessage = "Failed to load spatial unit: " + e.getMessage();
        }


        history = historyAuditService.findAllRevisionForEntity(SpatialUnit.class, idunit);
        documents = documentService.findForSpatialUnit(unit);
    }

    @Override
    public void cancelChanges() {
        unit.setGeom(backupClone.getGeom());
        unit.setName(backupClone.getName());
        unit.setValidated(backupClone.getValidated());
        unit.setArk(backupClone.getArk());
        unit.setCategory(backupClone.getCategory());
        formContext.setHasUnsavedModifications(false);
        initForms(true);
    }

    @Override
    public void init() {

        if (idunit == null) {
            this.spatialUnitErrorMessage = "The ID of the spatial unit must be defined";
            return;
        }

        refreshUnit();

        super.init();

        ActionTab actionTab = new ActionTab(
                "common.entity.actionUnits",
                "bi bi-arrow-down-square",
                "actionTab",
                totalActionUnitCount,
                actionTabTableModel);

        tabs.add(actionTab);

    }

    @Override
    public void visualise(RevisionWithInfo<SpatialUnit> history) {
        // spatialUnitHelperService.visualise(history, hist -> this.revisionToDisplay = hist);
    }

    public void restore(RevisionWithInfo<SpatialUnit> history) {
        spatialUnitHelperService.restore(history);
        init();
        MessageUtils.displayInfoMessage(langBean, "common.entity.spatialUnits.updated", history.getDate().toString());
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
            String langCode = sessionSettings.getLanguageCode();
            return list.stream()
                    .map(item -> (item instanceof Concept concept) ? labelService.findLabelOf(concept, langCode).getLabel() : item.toString())
                    .collect(Collectors.joining(", "));
        }

        return value.toString(); // Default case
    }

    @Override
    protected boolean documentExistsInUnitByHash(SpatialUnit unit, String hash) {
        return documentService.existInSpatialUnitByHash(unit, hash);
    }

    @Override
    protected void addDocumentToUnit(Document doc, SpatialUnit unit) {
        documentService.addToSpatialUnit(doc, unit);
    }




    @Override
    public boolean save(Boolean validated) {

        // Recupération des champs systeme

        // Name
        formContext.flushBackToEntity();

        unit.setValidated(validated);
        try {
            spatialUnitService.save(unit);
        }
        catch(FailedRecordingUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.spatialUnits.updateFailed", unit.getName());
            return false;
        }

        refreshUnit();
        MessageUtils.displayInfoMessage(langBean, "common.entity.spatialUnits.updated", unit.getName());
        return true;
    }

    public static class SpatialUnitPanelBuilder {

        private final SpatialUnitPanel spatialUnitPanel;

        public SpatialUnitPanelBuilder(ObjectProvider<SpatialUnitPanel> spatialUnitPanelProvider) {
            this.spatialUnitPanel = spatialUnitPanelProvider.getObject();
        }

        public SpatialUnitPanelBuilder id(Long id) {
            spatialUnitPanel.setIdunit(id);
            return this;
        }

        public SpatialUnitPanelBuilder activeIndex(Integer id) {
            spatialUnitPanel.setActiveTabIndex(id);
            return this;
        }

        public SpatialUnitPanelBuilder breadcrumb(PanelBreadcrumb breadcrumb) {
            spatialUnitPanel.setBreadcrumb(breadcrumb);

            return this;
        }

        public SpatialUnitPanel build() {
            spatialUnitPanel.init();
            return spatialUnitPanel;
        }
    }

    public void initActionTab() {
        ActionUnitInSpatialUnitLazyDataModel actionLazyDataModel = new ActionUnitInSpatialUnitLazyDataModel(
                actionUnitService,
                sessionSettings,
                langBean,
                unit
        );
        totalActionUnitCount = actionUnitService.countBySpatialContext(unit);
        ActionUnitTreeTableLazyModel actionLazyTree = new ActionUnitTreeTableLazyModel(
                actionUnitService, ActionUnitScope.builder()
                .spatialUnitId(unit.getId())
                .type(LINKED_TO_SPATIAL_UNIT)
                .build()
        );

        actionTabTableModel = new ActionUnitTableViewModel(
                actionLazyDataModel,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                flowBean,
                (GenericNewUnitDialogBean<ActionUnit>) genericNewUnitDialogBean,
                actionLazyTree,
                institutionService
        );

        ActionUnitTableDefinitionFactory.applyTo(actionTabTableModel);

        // configuration du bouton creer
        actionTabTableModel.setToolbarCreateConfig(
                ToolbarCreateConfig.builder()
                        .kindToCreate(UnitKind.ACTION)
                        .scopeSupplier(() ->
                                NewUnitContext.Scope.builder()
                                        .key("SPATIAL")
                                        .entityId(unit.getId())
                                        .build()
                        )
                        .build()
        );
    }

    public void initParentTableForHierarchyTab() {

        selectedCategoriesChildren = new ArrayList<>();
        SpatialUnitParentsLazyDataModel lazyDataModelParents= new SpatialUnitParentsLazyDataModel(
                spatialUnitService,
                langBean,
                unit
        );
        SpatialUnitTreeTableLazyModel parentLazyTree = new SpatialUnitTreeTableLazyModel(
                spatialUnitService, SpatialUnitScope.builder()
                .spatialUnitId(unit.getId())
                .institutionId(sessionSettingsBean.getSelectedInstitution().getId())
                .type(PARENTS_OF_SPATIAL_UNIT)
                .build()
        );
        totalChildrenCount = spatialUnitService.countChildrenByParent(unit);
        parentTableModel = new SpatialUnitTableViewModel(
                lazyDataModelParents,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                flowBean,
                (GenericNewUnitDialogBean<SpatialUnit>) genericNewUnitDialogBean,
                spatialUnitWriteVerifier,
                parentLazyTree,
                institutionService
        );
        SpatialUnitTableDefinitionFactory.applyTo(parentTableModel);

        // configuration du bouton creer
        parentTableModel.setToolbarCreateConfig(
                ToolbarCreateConfig.builder()
                        .kindToCreate(UnitKind.SPATIAL)
                        .scopeSupplier(() ->
                                NewUnitContext.Scope.builder()
                                        .key("SPATIAL")
                                        .entityId(unit.getId())
                                        .extra("PARENTS")
                                        .build()
                        )
                        .insertPolicySupplier(() -> NewUnitContext.UiInsertPolicy.builder()
                                .listInsert(NewUnitContext.ListInsert.TOP)
                                .treeInsert(NewUnitContext.TreeInsert.ROOT)
                                .build())
                        .build()
        );


    }

    public void initChildTableForHierarchyTab() {

        SpatialUnitChildrenLazyDataModel lazyDataModelChildren= new SpatialUnitChildrenLazyDataModel(
                spatialUnitService,
                langBean,
                unit
        );

        SpatialUnitTreeTableLazyModel childLazyTree = new SpatialUnitTreeTableLazyModel(
                spatialUnitService, SpatialUnitScope.builder()
                .spatialUnitId(unit.getId())
                .institutionId(sessionSettingsBean.getSelectedInstitution().getId())
                .type(CHILDREN_OF_SPATIAL_UNIT)
                .build()
        );
        totalParentsCount = spatialUnitService.countParentsByChild(unit);
        childTableModel = new SpatialUnitTableViewModel(
                lazyDataModelChildren,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                flowBean,
                (GenericNewUnitDialogBean<SpatialUnit>) genericNewUnitDialogBean,
                spatialUnitWriteVerifier,
                childLazyTree,
                institutionService
        );
        SpatialUnitTableDefinitionFactory.applyTo(childTableModel);

        // configuration du bouton creer
        childTableModel.setToolbarCreateConfig(
                ToolbarCreateConfig.builder()
                        .kindToCreate(UnitKind.SPATIAL)
                        .scopeSupplier(() ->
                                NewUnitContext.Scope.builder()
                                        .key("SPATIAL")
                                        .entityId(unit.getId())
                                        .extra("CHILDREN")
                                        .build()
                        )
                        .insertPolicySupplier(() -> NewUnitContext.UiInsertPolicy.builder()
                                .listInsert(NewUnitContext.ListInsert.TOP)
                                .treeInsert(NewUnitContext.TreeInsert.ROOT)
                                .build())
                        .build()
        );


    }

}