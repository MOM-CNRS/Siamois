package fr.siamois.ui.table.viewmodel;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.lazydatamodel.BaseContainerLazyDataModel;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.table.RowAction;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.column.RelationColumn;
import fr.siamois.ui.table.column.TableColumn;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.primefaces.model.TreeNode;

import java.util.List;

import static fr.siamois.ui.table.column.TableColumnAction.GO_TO_CONTAINER;

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

    private final ProfilePermissionService profilePermissionService;

    private final SessionSettingsBean sessionSettingsBean;

    private final ActionUnitService  actionUnitService;
    private final ActionUnitMapper actionUnitMapper;
    private final ContainerService containerService;


    public ContainerTableViewModel(BaseContainerLazyDataModel containerLazyDataModel,
                                   FormService formService,
                                   SessionSettingsBean sessionSettingsBean,
                                   SpatialUnitTreeService spatialUnitTreeService,
                                   SpatialUnitService spatialUnitService,
                                   NavBean navBean,
                                   FlowBean flowBean, GenericNewUnitDialogBean<ContainerDTO> genericNewUnitDialogBean,
                                   ProfilePermissionService profilePermissionService,
                                   FormContextServices formContextServices, ActionUnitService actionUnitService, ActionUnitMapper actionUnitMapper,
                                   ContainerService containerService) {

        super(
                containerLazyDataModel,
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
        this.profilePermissionService = profilePermissionService;
        this.actionUnitService = actionUnitService;
        this.actionUnitMapper = actionUnitMapper;
        this.containerService = containerService;
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

        if (column.getAction() == GO_TO_CONTAINER) {
            flowBean.addContainerToOverview(au.getId(), parentPanel, null, false);
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
    public void handleLinkEdit(CommandLinkColumn column, ContainerDTO item, String newValue) {
        String trimmed = newValue == null ? "" : newValue.trim();
        if (trimmed.isEmpty()) {
            MessageUtils.displayWarnMessage(langBean, "container.error.identifier.blank");
            return;
        }

        String previous = item.getIdentifier();
        item.setIdentifier(trimmed);

        if (containerService.identifierAlreadyExistInAction(item)) {
            item.setIdentifier(previous);
            MessageUtils.displayWarnMessage(langBean, "container.error.identifier.alreadyExists");
            return;
        }

        try {
            containerService.save(item);
        } catch (RuntimeException e) {
            item.setIdentifier(previous);
            MessageUtils.displayErrorMessage(sessionSettingsBean.getLangBean(), "common.entity.container.updateFailed", item.getIdentifier());
        }
    }

    @Override
    public Integer resolveCount(TableColumn column, ContainerDTO au) {
        return 0;
    }

    @Override
    public boolean isRendered(TableColumn column, String key, ContainerDTO au) {
        return switch (key) {
            case "writeMode" -> canUserEditRow(au);
            case "actionUnitCreateAllowed" -> profilePermissionService.hasOrganizationPermission(
                    flowBean.getSessionSettings().getUserInfo(), PermissionConstants.ORGANIZATION_MANAGE_ACTIONS);
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
                        .updateExpr("bookmarkToggleButton, subSidebarForm")
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


            default -> throw new IllegalStateException("Unhandled relation action: " + action);
        }
    }

    @SuppressWarnings("unused")
    public boolean isRendered(RowAction action, ContainerDTO au) {
        return switch (action.getAction()) {
            case DUPLICATE_ROW -> false;
            case TOGGLE_BOOKMARK -> false;
            default -> canUserEditRow(au);
        };
    }

    @SuppressWarnings("unused")
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
        return false;
    }

    @Override
    public boolean canUserEditRow(ContainerDTO unit) {
        Long actionUnitId = unit.getActionUnit() != null ? unit.getActionUnit().getId() : null;
        return canEditByActionUnit(profilePermissionService, sessionSettingsBean.getUserInfo(),
                PermissionConstants.PROJECT_EDIT_CONTAINERS, PermissionConstants.PROJECT_EDIT_CONTAINERS,
                c -> c.getActionUnit() != null ? c.getActionUnit().getId() : null, actionUnitId);
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
