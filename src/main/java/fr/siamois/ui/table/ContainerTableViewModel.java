package fr.siamois.ui.table;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.lazydatamodel.BaseContainerLazyDataModel;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.tree.ActionUnitTreeTableLazyModel;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
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
public class ContainerTableViewModel extends EntityTableViewModel<ContainerDTO, Long> {

    public static final String PARENTS = "parents";
    public static final String CHILDREN = "children";

    private final BaseContainerLazyDataModel containerLazyDataModel;
    private final FlowBean flowBean;

    private final InstitutionService institutionService;

    private final SessionSettingsBean sessionSettingsBean;

    private final ActionUnitService  actionUnitService;
    private final ActionUnitMapper actionUnitMapper;


    public ContainerTableViewModel(BaseContainerLazyDataModel containerLazyDataModel,
                                   FormService formService,
                                   SessionSettingsBean sessionSettingsBean,
                                   SpatialUnitTreeService spatialUnitTreeService,
                                   SpatialUnitService spatialUnitService,
                                   NavBean navBean,
                                   FlowBean flowBean, GenericNewUnitDialogBean<ContainerDTO> genericNewUnitDialogBean,
                                   ActionUnitTreeTableLazyModel treeLazyModel,
                                   InstitutionService institutionService,
                                   FormContextServices formContextServices, ActionUnitService actionUnitService, ActionUnitMapper actionUnitMapper) {

        super(
                containerLazyDataModel,
                null,
                genericNewUnitDialogBean,
                formService,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                sessionSettingsBean.getLangBean(),
                ContainerDTO::getId,   // idExtractor
                "type"        ,          // formScopeValueBinding,
                formContextServices
        );
        this.containerLazyDataModel = containerLazyDataModel;
        this.sessionSettingsBean = sessionSettingsBean;
        this.flowBean = flowBean;
        this.institutionService = institutionService;
        this.actionUnitService = actionUnitService;
        this.actionUnitMapper = actionUnitMapper;
    }

    @Override
    protected FormUiDto resolveRowFormFor(ContainerDTO au) {
        return null;
    }

    @Override
    protected void configureRowSystemFields(ContainerDTO au, FormUiDto rowForm) {
        // no system field to init
    }

    @Override
    protected void handleCommandLink(CommandLinkColumn column,
                                     ContainerDTO au) {

        if (column.getAction() == GO_TO_ACTION_UNIT) {

            flowBean.addActionUnitToOverview(
                    au.getId(),
                    parentPanel,
                    null
            );


        } else {
            throw new IllegalStateException(
                    "Unhandled action: " + column.getAction()
            );
        }
    }

    // resolving cell text based on value key
    @Override
    public String resolveText(TableColumn column, ContainerDTO au) {

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
    public Integer resolveCount(TableColumn column, ContainerDTO au) {
        if (column instanceof RelationColumn rel) {
            return switch (rel.getCountKey()) {
                default -> 0;
            };
        }
        return 0;
    }

    @Override
    public boolean isRendered(TableColumn column, String key, ContainerDTO au) {
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
    public void handleRelationAction(RelationColumn col, ContainerDTO au, TableColumnAction action) {
        switch (action) {
            case VIEW_RELATION ->
                    flowBean.addActionUnitToOverview(
                            au.getId(),
                            parentPanel,
                            col.getViewTargetIndex()
                    );

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

    public boolean isRendered(RowAction action, ContainerDTO au) {
        return switch (action.getAction()) {
            case DUPLICATE_ROW -> false;
            case TOGGLE_BOOKMARK -> false;
            default -> true;
        };
    }


    public String resolveIcon(RowAction action,
                              ContainerDTO au) {
        return switch (action.getAction()) {
            default -> "";
        };
    }

    public void handleRowAction(RowAction action,  Container au) {
        if (action == null || action.getAction() == null) {
            throw new IllegalStateException("Unhandled action: null");
        }

        throw new IllegalStateException("Unhandled action: " + action.getAction());
    }

    public void handleRowAction(RowAction action, TreeNode<Container> node) {
        Container au = node.getData();
        handleRowAction(action, au);
    }

    @Override
    public boolean isTreeViewSupported() {
        return true;
    }

    @Override
    public boolean canUserEditRow(ContainerDTO unit) {
        return true; // todo: implement permission
    }

    @Override
    public BaseLazyDataModel<ContainerDTO> getLazyDataModel() {
        containerLazyDataModel.setRootOnly(treeMode);
        return containerLazyDataModel;
    }

    @Override
    protected boolean unitIsLeaf(@NonNull ContainerDTO unit) {
        return true;
    }

    @Override
    protected @NonNull List<ContainerDTO> loadChildrensOfUnit(@NonNull ContainerDTO parentUnit) {
        //todo
        return List.of();
    }

}
