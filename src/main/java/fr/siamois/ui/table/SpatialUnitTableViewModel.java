package fr.siamois.ui.table;

import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitAlreadyExistsException;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.writeverifier.SpatialUnitWriteVerifier;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.lazydatamodel.BaseSpatialUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.tree.SpatialUnitTreeTableLazyModel;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import org.primefaces.model.TreeNode;

import java.util.*;

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
public class SpatialUnitTableViewModel extends EntityTableViewModel<SpatialUnit, Long> {

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
                                     FlowBean flowBean, GenericNewUnitDialogBean<SpatialUnit> genericNewUnitDialogBean,
                                     SpatialUnitWriteVerifier writeVerifier,
                                     SpatialUnitTreeTableLazyModel treeLazyModel,
                                     InstitutionService institutionService) {

        super(
                lazyDataModel,
                treeLazyModel,
                genericNewUnitDialogBean,
                formService,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                SpatialUnit::getId,   // idExtractor
                "type"                  // formScopeValueBinding
        );
        this.spatialUnitLazyDataModel = lazyDataModel;
        this.sessionSettingsBean = sessionSettingsBean;
        this.flowBean = flowBean;

        this.spatialUnitWriteVerifier = writeVerifier;
        this.institutionService = institutionService;
    }

    @Override
    protected CustomForm resolveRowFormFor(SpatialUnit su) {
        return null;
    }

    @Override
    protected void configureRowSystemFields(SpatialUnit su, CustomForm rowForm) {
       // no system field to configure
    }

    @Override
    protected void handleCommandLink(CommandLinkColumn column,
                                     SpatialUnit su,
                                     Integer panelIndex) {

        if (column.getAction() == GO_TO_SPATIAL_UNIT) {
            flowBean.goToSpatialUnitByIdNewPanel(su.getId(), panelIndex);
        } else {
            throw new IllegalStateException("Unhandled action: " + column.getAction());
        }

    }

    // resolving cell text based on value key
    @Override
    public String resolveText(TableColumn column, SpatialUnit su) {

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
    public Integer resolveCount(TableColumn column, SpatialUnit su) {
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
    public boolean isRendered(TableColumn column, String key, SpatialUnit su, Integer panelIndex) {
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
    public void handleRelationAction(RelationColumn col, SpatialUnit su, Integer panelIndex, TableColumnAction action) {
        switch (action) {

            case VIEW_RELATION -> flowBean.goToSpatialUnitByIdNewPanel(su.getId(), panelIndex, col.getViewTargetIndex());

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

    public boolean isRendered(RowAction action, SpatialUnit su) {
        // todo: display based on permissions
        return switch (action.getAction()) {
            case DUPLICATE_ROW, NEW_CHILDREN -> flowBean.getIsWriteMode() && // perm to create spatial unit in orga and app is in write mode
                    spatialUnitService.hasCreatePermission(sessionSettingsBean.getUserInfo());
            case TOGGLE_BOOKMARK -> true; // Anyone can add to fav
            case NEW_ACTION -> flowBean.getIsWriteMode() && // perm to create action unit in orga and app is in write mode
                    institutionService.personIsInstitutionManagerOrActionManager(sessionSettingsBean.getUserInfo().getUser(),
                            sessionSettingsBean.getSelectedInstitution());
            default -> true;
        };
    }


    public String resolveIcon(RowAction action, SpatialUnit su) {
            return switch (action.getAction()) {
                case TOGGLE_BOOKMARK -> Boolean.TRUE.equals(navBean.isSpatialUnitBookmarkedByUser(su.getId()))
                        ? "bi bi-bookmark-x-fill"
                        : "bi bi-bookmark-plus";
                case DUPLICATE_ROW -> "bi bi-copy";
                case NEW_ACTION -> "bi bi-arrow-down-square";
                case NEW_CHILDREN -> "bi bi-node-plus-fill rotate-90";
                default -> "";
            };
    }

    public void handleRowAction(RowAction action, SpatialUnit su) {

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

    // actions specific to treetable
    public void handleRowAction(RowAction action, TreeNode<SpatialUnit> node) {
        SpatialUnit su = node.getData();

        if (action.getAction() == DUPLICATE_ROW) {
            if (!Objects.equals(node.getParent().getRowKey(), "root")) {
                this.duplicateRow(node.getData(), node.getParent().getData());
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
        Set<SpatialUnit> entities;
        if (treeMode) {
            entities = treeLazyModel.getAllEntitiesFromTree();
        } else {
            entities = new HashSet<>(lazyDataModel.getQueryResult());
        }

        // Iterate over all entities
        for (SpatialUnit entity : entities) {
            Long entityId = entity.getId();
            EntityFormContext<SpatialUnit> context = rowContexts.get(entityId);

            // Check if the entity has been modified
            if (context != null && context.isHasUnsavedModifications()) {
                try {
                    // Save the entity
                    context.flushBackToEntity();

                    // todo : IF the user doesnt have right to validate the unit, it will be unvalidated.

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
    public TreeNode<SpatialUnit> getTreeRoot() {
        return treeLazyModel.getRoot();
    }

    // Duplique une unité spatiale
    // Le place au même niveau dans la hierarchie mais ne copie pas les enfants
    private void duplicateRow(SpatialUnit toDuplicate, SpatialUnit parent) {

        // Create a copy from selected row
        SpatialUnit newUnit = new SpatialUnit(toDuplicate);

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
