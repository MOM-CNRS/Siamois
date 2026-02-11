package fr.siamois.ui.table;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.lazydatamodel.BaseActionUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.tree.ActionUnitTreeTableLazyModel;
import lombok.Getter;
import org.primefaces.model.TreeNode;

import java.util.List;

import static fr.siamois.ui.table.TableColumnAction.GO_TO_ACTION_UNIT;

/**
 * View model spécifique pour les tableaux de ActionUnit.
 *
 * - spécialise EntityTableViewModel pour T = ActionUnit, ID = Long
 * - implémente :
 *      - resolveRowFormFor
 *      - configureRowSystemFields
 */
@Getter
public class ActionUnitTableViewModel extends EntityTableViewModel<ActionUnit, Long> {

    public static final String PARENTS = "parents";
    public static final String CHILDREN = "children";
    /** Lazy model spécifique RecordingUnit (accès à selectedUnits, etc.) */
    private final BaseActionUnitLazyDataModel actionUnitLazyDataModel;
    private final FlowBean flowBean;

    private final InstitutionService institutionService;

    private final SessionSettingsBean sessionSettingsBean;


    public ActionUnitTableViewModel(BaseActionUnitLazyDataModel actionUnitLazyDataModel,
                                    FormService formService,
                                    SessionSettingsBean sessionSettingsBean,
                                    SpatialUnitTreeService spatialUnitTreeService,
                                    SpatialUnitService spatialUnitService,
                                    NavBean navBean,
                                    FlowBean flowBean, GenericNewUnitDialogBean<ActionUnit> genericNewUnitDialogBean,
                                    ActionUnitTreeTableLazyModel treeLazyModel,
                                    InstitutionService institutionService,
                                    FormContextServices formContextServices) {

        super(
                actionUnitLazyDataModel,
                treeLazyModel,
                genericNewUnitDialogBean,
                formService,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                sessionSettingsBean.getLangBean(),
                ActionUnit::getId,   // idExtractor
                "type"        ,          // formScopeValueBinding,
                formContextServices
        );
        this.actionUnitLazyDataModel = actionUnitLazyDataModel;
        this.sessionSettingsBean = sessionSettingsBean;
        this.flowBean = flowBean;
        this.institutionService = institutionService;
    }

    @Override
    protected CustomForm resolveRowFormFor(ActionUnit au) {
        return null;
    }

    @Override
    protected void configureRowSystemFields(ActionUnit au, CustomForm rowForm) {
        // no system field to init
    }

    @Override
    protected void handleCommandLink(CommandLinkColumn column,
                                     ActionUnit au) {

        if (column.getAction() == GO_TO_ACTION_UNIT) {

            flowBean.goToActionUnitByIdNewPanel(
                    au.getId()
            );

        } else {
            throw new IllegalStateException(
                    "Unhandled action: " + column.getAction()
            );
        }
    }

    // resolving cell text based on value key
    @Override
    public String resolveText(TableColumn column, ActionUnit au) {

        if (column instanceof CommandLinkColumn linkColumn) {

            String valueKey = linkColumn.getValueKey();

            if ("identifier".equals(valueKey)) {
                return au.getIdentifier();
            }

            throw new IllegalStateException("Unknown valueKey: " + valueKey);
        }


        return "";
    }

    @Override
    public Integer resolveCount(TableColumn column, ActionUnit au) {
        if (column instanceof RelationColumn rel) {
            return switch (rel.getCountKey()) {
                case PARENTS -> au.getParents() == null ? 0 : au.getParents().size();
                case CHILDREN -> au.getChildren() == null ? 0 : au.getChildren().size();
                case "recordingUnit" -> au.getRecordingUnitList() == null ? 0 : au.getRecordingUnitList().size();
                default -> 0;
            };
        }
        return 0;
    }

    @Override
    public boolean isRendered(TableColumn column, String key, ActionUnit au) {
        return switch (key) {
            case "writeMode" -> flowBean.getIsWriteMode();
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
                        .processExpr("@this")
                        .updateExpr("bookmarkToggleButton navBarCsrfForm:siamoisNavForm:bookmarkGroup")
                        .updateSelfTable(false)
                        .styleClass("sia-icon-btn")
                        .build(),

                // Duplicate row (SpatialUnit only)
                RowAction.builder()
                        .action(TableColumnAction.DUPLICATE_ROW)
                        .processExpr("@this")
                        .updateSelfTable(true) // <-- mettra à jour :#{cc.clientId}:entityDatatable
                        .styleClass("sia-icon-btn")
                        .build()
        );
    }


    @Override
    public void handleRelationAction(RelationColumn col, ActionUnit au, TableColumnAction action) {
        switch (action) {

            case VIEW_RELATION ->
                    flowBean.goToActionUnitByIdNewPanel(au.getId(), col.getViewTargetIndex());

            case ADD_RELATION -> {
                // Dispatch based on column.countKey (or add a dedicated "relationKey")
                String countKey = col.getCountKey();

                if (PARENTS.equals(countKey)) {

                    NewUnitContext ctx = NewUnitContext.builder()
                            .kindToCreate(UnitKind.ACTION)
                            .trigger(NewUnitContext.Trigger.cell(UnitKind.ACTION, au.getId(), PARENTS))
                            .insertPolicy(NewUnitContext.UiInsertPolicy.builder()
                                    .listInsert(NewUnitContext.ListInsert.TOP)
                                    .treeInsert(NewUnitContext.TreeInsert.PARENT_AT_ROOT)
                                    .build())
                            .build();

                    openCreateDialog(ctx, genericNewUnitDialogBean);

                } else if (CHILDREN.equals(countKey)) {

                    NewUnitContext ctx = NewUnitContext.builder()
                            .kindToCreate(UnitKind.ACTION)
                            .trigger(NewUnitContext.Trigger.cell(UnitKind.ACTION, au.getId(), CHILDREN))
                            .insertPolicy(NewUnitContext.UiInsertPolicy.builder()
                                    .listInsert(NewUnitContext.ListInsert.TOP)
                                    .treeInsert(NewUnitContext.TreeInsert.CHILD_FIRST)
                                    .build())
                            .build();

                    openCreateDialog(ctx, genericNewUnitDialogBean);
                }

            }

            default -> throw new IllegalStateException("Unhandled relation action: " + action);
        }
    }

    public boolean isRendered(RowAction action, ActionUnit au) {
        return switch (action.getAction()) {
            case DUPLICATE_ROW -> false;
            case TOGGLE_BOOKMARK -> false;
            default -> true;
        };
    }


    public String resolveIcon(RowAction action,
                              ActionUnit au) {
        return switch (action.getAction()) {
            default -> "";
        };
    }

    public void handleRowAction(RowAction action,  ActionUnit au) {
        if (action == null || action.getAction() == null) {
            throw new IllegalStateException("Unhandled action: null");
        }

        throw new IllegalStateException("Unhandled action: " + action.getAction());
    }

    public void handleRowAction(RowAction action, TreeNode<ActionUnit> node) {
        ActionUnit au = node.getData();
        handleRowAction(action, au);
    }

    @Override
    public boolean isTreeViewSupported() {
        return true;
    }

    @Override
    public boolean canUserEditRow(ActionUnit unit) {
        return true; // todo: implement permission
    }

    @Override
    public TreeNode<ActionUnit> getTreeRoot() {
        return treeLazyModel.getRoot();
    }

}
