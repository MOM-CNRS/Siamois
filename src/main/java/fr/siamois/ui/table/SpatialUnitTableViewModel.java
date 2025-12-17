package fr.siamois.ui.table;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.CustomFieldInteger;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.authorization.writeverifier.RecordingUnitWriteVerifier;
import fr.siamois.domain.services.authorization.writeverifier.SpatialUnitWriteVerifier;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.lazydatamodel.BaseRecordingUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.BaseSpatialUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.tree.RecordingUnitTreeTableLazyModel;
import fr.siamois.ui.lazydatamodel.tree.SpatialUnitTreeTableLazyModel;
import lombok.Getter;
import org.primefaces.model.TreeNode;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

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

    /** Lazy model spécifique RecordingUnit (accès à selectedUnits, etc.) */
    private final BaseSpatialUnitLazyDataModel spatialUnitLazyDataModel;
    private final FlowBean flowBean;

    private final SpatialUnitWriteVerifier spatialUnitWriteVerifier;

    private final SessionSettingsBean sessionSettingsBean;
    private final SpatialUnitTreeTableLazyModel treeLazyModel;

    public SpatialUnitTableViewModel(BaseSpatialUnitLazyDataModel lazyDataModel,
                                     FormService formService,
                                     SessionSettingsBean sessionSettingsBean,
                                     SpatialUnitTreeService spatialUnitTreeService,
                                     SpatialUnitService spatialUnitService,
                                     NavBean navBean,
                                     FlowBean flowBean, GenericNewUnitDialogBean<SpatialUnit> genericNewUnitDialogBean,
                                     SpatialUnitWriteVerifier writeVerifier,
                                     SpatialUnitTreeTableLazyModel treeLazyModel) {

        super(
                lazyDataModel,
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

        this.treeLazyModel = treeLazyModel;
    }

    @Override
    protected CustomForm resolveRowFormFor(SpatialUnit su) {
        return null;
        // todo
    }

    @Override
    protected void configureRowSystemFields(SpatialUnit su, CustomForm rowForm) {
        if (rowForm == null || rowForm.getLayout() == null) {
            return;
        }

    }

    @Override
    protected void handleCommandLink(CommandLinkColumn column,
                                     SpatialUnit su,
                                     Integer panelIndex) {

        switch (column.getAction()) {

            case GO_TO_SPATIAL_UNIT ->
                    flowBean.goToSpatialUnitByIdNewPanel(
                            su.getId(),
                            panelIndex
                    );

            default -> throw new IllegalStateException(
                    "Unhandled action: " + column.getAction()
            );
        }
    }

    // resolving cell text based on value key
    @Override
    public String resolveText(TableColumn column, SpatialUnit su) {

        if (column instanceof CommandLinkColumn linkColumn) {

            switch (linkColumn.getValueKey()) {

                case "name":
                    return su.getName();

                default:
                    throw new IllegalStateException(
                            "Unknown valueKey: " + linkColumn.getValueKey()
                    );
            }
        }

        return "";
    }

    @Override
    public Integer resolveCount(TableColumn column, SpatialUnit su) {
        if (column instanceof RelationColumn rel) {
            return switch (rel.getCountKey()) {
                case "parents" -> su.getParents() == null ? 0 : su.getParents().size();
                case "children" -> su.getChildren() == null ? 0 : su.getChildren().size();
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
    public void handleRelationAction(RelationColumn col, SpatialUnit su, Integer panelIndex, TableColumnAction action) {
        switch (action) {

            case VIEW_RELATION ->
                    flowBean.goToRecordingUnitByIdNewPanel(su.getId(), panelIndex, col.getViewTargetIndex());

            case ADD_RELATION -> {
                // Dispatch based on column.countKey (or add a dedicated "relationKey")
                switch (col.getCountKey()) {
                    case "parents" ->
                            trySelectKind(
                                    UnitKind.SPATIAL,
                                    su.getParents(),
                                    null,
                                    su
                            );

                    case "children" ->
                            trySelectKind(
                                    UnitKind.SPATIAL,
                                    su.getChildren(),
                                    su,
                                    null
                            );

                    case "action" ->
                            trySelectKind(
                                    UnitKind.ACTION,
                                    su.getRelatedActionUnitList(),
                                    su
                            );
                }
            }

            default -> throw new IllegalStateException("Unhandled relation action: " + action);
        }
    }

    public boolean isRendered(RowAction action, SpatialUnit su) {
        return switch (action.getAction()) {
            case DUPLICATE_ROW -> false;
            case TOGGLE_BOOKMARK -> false;
            default -> true;
        };
    }


    public String resolveIcon(RowAction action, SpatialUnit su) {
        return switch (action.getAction()) {
            default -> "";
        };
    }
    public void handleRowAction(RowAction action, SpatialUnit su) {
        switch (action.getAction()) {
            default -> throw new IllegalStateException("Unhandled action: " + action.getAction());
        }
    }

    @Override
    public boolean isTreeViewSupported() {
        return true;
    }

    @Override
    public TreeNode<SpatialUnit> getTreeRoot() {
        return treeLazyModel.getRoot();
    }

}
