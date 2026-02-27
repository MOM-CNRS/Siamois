package fr.siamois.ui.table;

import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitAlreadyExistsException;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.writeverifier.SpatialUnitWriteVerifier;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.form.FormUiDto;
import fr.siamois.ui.lazydatamodel.BaseSpatialUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.tree.SpatialUnitTreeTableLazyModel;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import org.primefaces.model.TreeNode;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static fr.siamois.ui.bean.dialog.newunit.NewUnitContext.TreeInsert.ROOT;
import static fr.siamois.ui.table.TableColumnAction.DUPLICATE_ROW;
import static fr.siamois.ui.table.TableColumnAction.GO_TO_SPATIAL_UNIT;

/**
 * View model spécifique pour les tableaux de SpatialUnit.
 *
 * - spécialise EntityTableViewModel pour T = SpatialUnit, ID = Long
 * - implémente :
 *      - resolveRowFormFor
 *      - configureRowSystemFields
 */
@Getter
public class SpatialUnitTableViewModel extends EntityTableViewModel<SpatialUnitDTO, Long> {

    public static final String PARENTS = "parents";
    public static final String CHILDREN = "children";
    public static final String THIS = "@this";
    public static final String SIA_ICON_BTN = "sia-icon-btn";
    /** Lazy model spécifique RecordingUnit (accès à selectedUnits, etc.) */
    private final BaseSpatialUnitLazyDataModel spatialUnitLazyDataModel;
    private final FlowBean flowBean;

    private final SpatialUnitWriteVerifier spatialUnitWriteVerifier;
    private final InstitutionService institutionService;

    private final SessionSettingsBean sessionSettingsBean;


    public SpatialUnitTableViewModel(BaseSpatialUnitLazyDataModel lazyDataModel,
                                     FormService formService,
                                     SessionSettingsBean sessionSettingsBean,
                                     SpatialUnitTreeService spatialUnitTreeService,
                                     SpatialUnitService spatialUnitService,
                                     NavBean navBean,
                                     FlowBean flowBean, GenericNewUnitDialogBean<SpatialUnitDTO> genericNewUnitDialogBean,
                                     SpatialUnitWriteVerifier writeVerifier,
                                     SpatialUnitTreeTableLazyModel treeLazyModel,
                                     InstitutionService institutionService,
                                     FormContextServices formContextService) {

        super(
                lazyDataModel,
                treeLazyModel,
                genericNewUnitDialogBean,
                formService,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                sessionSettingsBean.getLangBean(),
                SpatialUnitDTO::getId,   // idExtractor
                "type"  ,         // formScopeValueBinding,
                formContextService
        );
        this.spatialUnitLazyDataModel = lazyDataModel;
        this.sessionSettingsBean = sessionSettingsBean;
        this.flowBean = flowBean;

        this.spatialUnitWriteVerifier = writeVerifier;
        this.institutionService = institutionService;
    }

    @Override
    protected FormUiDto resolveRowFormFor(SpatialUnitDTO su) {
        return null;
    }

    @Override
    protected void configureRowSystemFields(SpatialUnitDTO su, FormUiDto rowForm) {
       // no system field to configure
    }

    @Override
    protected void handleCommandLink(CommandLinkColumn column,
                                     SpatialUnitDTO su) {

        if (column.getAction() == GO_TO_SPATIAL_UNIT) {
            flowBean.goToSpatialUnitByIdNewPanel(su.getId());
        } else {
            throw new IllegalStateException("Unhandled action: " + column.getAction());
        }

    }

    // resolving cell text based on value key
    @Override
    public String resolveText(TableColumn column, SpatialUnitDTO su) {

        if (column instanceof CommandLinkColumn linkColumn) {

            if ("name".equals(linkColumn.getValueKey())) {
                return su.getName();
            } else {
                throw new IllegalStateException("Unknown valueKey: " + linkColumn.getValueKey());
            }

        }

        return "";
    }

    @Override
    public Integer resolveCount(TableColumn column, SpatialUnitDTO su) {
        if (column instanceof RelationColumn rel) {
            return switch (rel.getCountKey()) {
                case PARENTS -> su.getParents() == null ? 0 : su.getParents().size();
                case CHILDREN -> su.getChildren() == null ? 0 : su.getChildren().size();
                case "action" -> su.getRelatedActionUnitList() == null ? 0 : su.getRelatedActionUnitList().size();
                case "recordingUnit" -> su.getRecordingUnitList() == null ? 0 : su.getRecordingUnitList().size();
                default -> 0;
            };
        }
        return 0;
    }

    @Override
    public boolean isRendered(TableColumn column, String key, SpatialUnitDTO su) {
        return switch (key) {
            case "writeMode" -> flowBean.getIsWriteMode();
            case "spatialUnitCreateAllowed" -> spatialUnitWriteVerifier.hasSpecificWritePermission(flowBean.getSessionSettings().getUserInfo(), su);
            case "actionUnitCreateAllowed" -> institutionService.personIsInstitutionManagerOrActionManager(
                    flowBean.getSessionSettings().getUserInfo().getUser(),
                    flowBean.getSessionSettings().getSelectedInstitution());
            default -> false;
        };
    }



    @Override
    public List<RowAction> getRowActions() {
        return List.of(

                // Bookmark toggle
                RowAction.builder()
                        .action(TableColumnAction.TOGGLE_BOOKMARK)
                        .processExpr(THIS)
                        .updateExpr("@this navBarCsrfForm:siamoisNavForm:bookmarkGroup")
                        .updateSelfTable(false)
                        .styleClass(SIA_ICON_BTN)
                        .build(),

                // Duplicate row (SpatialUnit only)
                RowAction.builder()
                        .action(DUPLICATE_ROW)
                        .processExpr(THIS)
                        .updateSelfTable(true)
                        .styleClass(SIA_ICON_BTN)
                        .build(),

                // Add children
                RowAction.builder()
                        .action(TableColumnAction.NEW_CHILDREN)
                        .processExpr(THIS)
                        .updateSelfTable(true)
                        .styleClass(SIA_ICON_BTN)
                        .build(),

                // Add Parent
                RowAction.builder()
                        .action(TableColumnAction.NEW_PARENT)
                        .processExpr(THIS)
                        .updateSelfTable(true)
                        .styleClass(SIA_ICON_BTN)
                        .build(),

                // New action
                RowAction.builder()
                        .action(TableColumnAction.NEW_ACTION)
                        .processExpr(THIS)
                        .updateSelfTable(false)
                        .styleClass(SIA_ICON_BTN)
                        .build()
        );
    }


    @Override
    public void handleRelationAction(RelationColumn col, SpatialUnitDTO su, TableColumnAction action) {
        switch (action) {

            case VIEW_RELATION -> flowBean.goToSpatialUnitByIdNewPanel(su.getId());

            case ADD_RELATION -> {
                // Dispatch based on column.countKey (or add a dedicated "relationKey")
                switch (col.getCountKey()) {
                    case PARENTS -> {
                        NewUnitContext ctx = NewUnitContext.builder()
                                .kindToCreate(UnitKind.SPATIAL)
                                .trigger(NewUnitContext.Trigger.cell(UnitKind.SPATIAL, su.getId(), PARENTS))
                                .insertPolicy(NewUnitContext.UiInsertPolicy.builder()
                                        .listInsert(NewUnitContext.ListInsert.TOP)
                                        .treeInsert(NewUnitContext.TreeInsert.PARENT_AT_ROOT)
                                        .build())
                                .build();

                        openCreateDialog(ctx, genericNewUnitDialogBean);
                    }


                    case "actions" -> {
                        NewUnitContext ctx = NewUnitContext.builder()
                                .kindToCreate(UnitKind.ACTION)
                                .trigger(NewUnitContext.Trigger.cell(UnitKind.SPATIAL, su.getId(), "related_actions"))
                                .insertPolicy(null)
                                .build();

                        openCreateDialog(ctx, genericNewUnitDialogBean);
                    }

                    default -> {
                        // no op
                    }

                }
            }

            default -> throw new IllegalStateException("Unhandled relation action: " + action);
        }
    }

    public boolean isRendered(RowAction action, SpatialUnitDTO su) {
        // todo: display based on permissions
        return switch (action.getAction()) {
            case DUPLICATE_ROW, NEW_CHILDREN, NEW_PARENT -> flowBean.getIsWriteMode() && // perm to create spatial unit in orga and app is in write mode
                    spatialUnitService.hasCreatePermission(sessionSettingsBean.getUserInfo());
            case TOGGLE_BOOKMARK -> true; // Anyone can add to fav
            case NEW_ACTION -> flowBean.getIsWriteMode() && // perm to create action unit in orga and app is in write mode
                    institutionService.personIsInstitutionManagerOrActionManager(sessionSettingsBean.getUserInfo().getUser(),
                            sessionSettingsBean.getSelectedInstitution());
            default -> true;
        };
    }


    public String resolveIcon(RowAction action, SpatialUnitDTO su) {
            return switch (action.getAction()) {
                case TOGGLE_BOOKMARK -> Boolean.TRUE.equals(navBean.isSpatialUnitBookmarkedByUser(su.getId()))
                        ? "bi bi-bookmark-x-fill"
                        : "bi bi-bookmark-plus";
                case DUPLICATE_ROW -> "bi bi-copy";
                case NEW_ACTION -> "bi bi-arrow-down-square";
                case NEW_CHILDREN -> "bi bi-node-plus-fill rotate-90";
                case NEW_PARENT -> "bi bi-node-plus-fill rotate-minus90";
                default -> "";
            };
    }

    public void handleRowAction(RowAction action, SpatialUnitDTO su) {

        switch (action.getAction()) {

            case TOGGLE_BOOKMARK -> navBean.toggleSpatialUnitBookmark(su);

            case DUPLICATE_ROW -> this.duplicateRow(su, null);

            case NEW_CHILDREN -> {
                // Open new spatial unit dialog
                // The new spatial unit will be children of the current su
                NewUnitContext ctx = NewUnitContext.builder()
                        .kindToCreate(UnitKind.SPATIAL)
                        .trigger(NewUnitContext.Trigger.cell(UnitKind.SPATIAL, su.getId(), CHILDREN))
                        .insertPolicy(NewUnitContext.UiInsertPolicy.builder()
                                .listInsert(NewUnitContext.ListInsert.TOP)
                                .treeInsert(NewUnitContext.TreeInsert.CHILD_FIRST)
                                .build())
                        .build();

                openCreateDialog(ctx, genericNewUnitDialogBean);
            }

            case NEW_PARENT -> {
                // Open new spatial unit dialog
                // The new spatial unit will be PARENT of the current su
                NewUnitContext ctx = NewUnitContext.builder()
                        .kindToCreate(UnitKind.SPATIAL)
                        .trigger(NewUnitContext.Trigger.cell(UnitKind.SPATIAL, su.getId(), PARENTS))
                        .insertPolicy(NewUnitContext.UiInsertPolicy.builder()
                                .listInsert(NewUnitContext.ListInsert.TOP)
                                .treeInsert(NewUnitContext.TreeInsert.PARENT_AT_ROOT)
                                .build())
                        .build();

                openCreateDialog(ctx, genericNewUnitDialogBean);
            }

            case NEW_ACTION -> {
                // Open new action unit dialog
                // The new action unit will have the current unit as spatial context
                NewUnitContext ctx = NewUnitContext.builder()
                        .kindToCreate(UnitKind.ACTION)
                        .trigger(NewUnitContext.Trigger.cell(UnitKind.SPATIAL, su.getId(), "related_actions"))
                        .insertPolicy(null)
                        .build();

                openCreateDialog(ctx, genericNewUnitDialogBean);
            }

            default -> {
                // no op
            }
        }
    }

    @Override
    public String getRowActionTooltipCode(RowAction action, SpatialUnitDTO unit) {

        return switch (action.getAction()) {

            case TOGGLE_BOOKMARK ->  Boolean.TRUE.equals(navBean.isSpatialUnitBookmarkedByUser(unit.getId()))
                    ? sessionSettingsBean.getLangBean().msg("common.action.unbookmark")
                    : sessionSettingsBean.getLangBean().msg("common.action.bookmark") ;

            case DUPLICATE_ROW -> sessionSettingsBean.getLangBean().msg("common.action.duplicate") ;

            case NEW_CHILDREN -> sessionSettingsBean.getLangBean().msg("common.action.createChildren") ;

            case NEW_PARENT -> sessionSettingsBean.getLangBean().msg("common.action.createParent") ;

            case NEW_ACTION -> sessionSettingsBean.getLangBean().msg("common.action.createAction") ;

            default -> null;
        };
    }

    // actions specific to treetable
    public void handleRowAction(RowAction action, TreeNode<SpatialUnitDTO> node) {
        SpatialUnitDTO su = node.getData();

        if (action.getAction() == DUPLICATE_ROW) {
            if (!Objects.equals(node.getParent().getRowKey(), "root")) {
                this.duplicateRow(node.getData(), new SpatialUnitSummaryDTO(node.getParent().getData()));
                return;
            }
            this.duplicateRow(node.getData(), null);
        } else {
            handleRowAction(action, su);
        }
    }

    @Override
    public boolean isTreeViewSupported() {
        return true;
    }

    @Override
    public void save() {
        // Determine the source of entities based on treeMode
        Set<SpatialUnitDTO> entities;
        if (treeMode) {
            entities = treeLazyModel.getAllEntitiesFromTree();
        } else {
            entities = new HashSet<>(lazyDataModel.getQueryResult());
        }

        // Iterate over all entities
        for (SpatialUnitDTO entity : entities) {
            Long entityId = entity.getId();
            EntityFormContext<SpatialUnitDTO> context = rowContexts.get(entityId);

            // Check if the entity has been modified
            if (context != null && context.isHasUnsavedModifications()) {
                try {
                    // Save the entity
                    context.flushBackToEntity();

                    spatialUnitService.save(entity);

                    context.init(true);

                } catch (FailedRecordingUnitSaveException e) {
                    // Display error message
                    MessageUtils.displayErrorMessage(sessionSettingsBean.getLangBean(), "common.entity.spatialUnits.updateFailed", entity.getName());
                }
            }
        }
    }



    @Override
    public boolean canUserEditRow(SpatialUnitDTO unit) {
        return flowBean.getIsWriteMode() && // perm to create action unit in orga and app is in write mode
                institutionService.personIsInstitutionManagerOrActionManager(sessionSettingsBean.getUserInfo().getUser(),
                        sessionSettingsBean.getSelectedInstitution());
    }

    @Override
    public TreeNode<SpatialUnitDTO> getTreeRoot() {
        return treeLazyModel.getRoot();
    }

    // Duplique une unité spatiale
    // Le place au même niveau dans la hierarchie mais ne copie pas les enfants
    private void duplicateRow(SpatialUnitDTO toDuplicate, SpatialUnitSummaryDTO parent) {

        // Create a copy from selected row
        SpatialUnitDTO newUnit = new SpatialUnitDTO(toDuplicate);

        if(parent != null) {
            newUnit.getParents().add(parent);
        }

        String baseName = toDuplicate.getName();

        int maxAttempts = 5;

        for (int i = 1; i <= maxAttempts; i++) {
            String candidateName = baseName + " (" + i + ")";
            newUnit.setName(candidateName);

            if (parent != null) {
                newUnit.getParents().add(parent);
            }

            try {
                newUnit = spatialUnitService.save(
                        sessionSettingsBean.getUserInfo(),
                        newUnit
                );
                break;
            } catch (SpatialUnitAlreadyExistsException e) {
                if (i == maxAttempts) {
                    MessageUtils.displayErrorMessage(
                            sessionSettingsBean.getLangBean(),
                            "common.entity.spatialUnits.updateFailed",
                            candidateName
                    );
                    return;
                }
                // sinon on continue
            }
        }

        // Build the creation context (as child of the parent of the duplicated row, or root if no parent)
        NewUnitContext ctx = NewUnitContext.builder()
                .kindToCreate(UnitKind.SPATIAL)
                .trigger(
                        parent == null
                                ? null
                                : NewUnitContext.Trigger.cell(
                                UnitKind.SPATIAL,
                                parent.getId(),
                                PARENTS
                        )
                )
                .insertPolicy(NewUnitContext.UiInsertPolicy.builder()
                        .listInsert(NewUnitContext.ListInsert.TOP)
                        .treeInsert(parent == null ? ROOT : NewUnitContext.TreeInsert.CHILD_FIRST)
                        .build())
                .build();


        onAnyEntityCreated(newUnit, ctx) ;
        MessageUtils.displayInfoMessage(sessionSettingsBean.getLangBean(), "common.action.duplicateEntity", toDuplicate.getName());
    }

}
