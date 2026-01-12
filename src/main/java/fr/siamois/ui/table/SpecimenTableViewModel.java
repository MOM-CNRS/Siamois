package fr.siamois.ui.table;

import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.lazydatamodel.BaseSpecimenLazyDataModel;
import lombok.Getter;
import org.primefaces.model.TreeNode;

import java.util.List;

import static fr.siamois.ui.table.TableColumnAction.DUPLICATE_ROW;
import static fr.siamois.ui.table.TableColumnAction.GO_TO_SPECIMEN;

/**
 * View model spécifique pour les tableaux de RecordingUnit.
 *
 * - spécialise EntityTableViewModel pour T = RecordingUnit, ID = Long
 * - implémente :
 *      - resolveRowFormFor
 *      - configureRowSystemFields
 */
@Getter
public class SpecimenTableViewModel extends EntityTableViewModel<Specimen, Long> {

    /** Lazy model spécifique RecordingUnit (accès à selectedUnits, etc.) */
    private final BaseSpecimenLazyDataModel specimenLazyDataModel;
    private final FlowBean flowBean;



    private final SessionSettingsBean sessionSettingsBean;

    public SpecimenTableViewModel(BaseSpecimenLazyDataModel lazyDataModel,
                                  FormService formService,
                                  SessionSettingsBean sessionSettingsBean,
                                  SpatialUnitTreeService spatialUnitTreeService,
                                  SpatialUnitService spatialUnitService,
                                  NavBean navBean,
                                  FlowBean flowBean, GenericNewUnitDialogBean<Specimen> genericNewUnitDialogBean) {

        super(
                lazyDataModel,
                null,
                genericNewUnitDialogBean,
                formService,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                Specimen::getId,   // idExtractor
                "type"                  // formScopeValueBinding
        );
        this.specimenLazyDataModel = lazyDataModel;
        this.sessionSettingsBean = sessionSettingsBean;
        this.flowBean = flowBean;


    }

    @Override
    protected CustomForm resolveRowFormFor(Specimen s) {
        return null;
    }

    @Override
    protected void configureRowSystemFields(Specimen s, CustomForm rowForm) {
        // no specific configs
    }

    @Override
    protected void handleCommandLink(CommandLinkColumn column,
                                     Specimen s,
                                     Integer panelIndex) {

        if (column.getAction() == GO_TO_SPECIMEN) {
            flowBean.goToSpecimenByIdNewPanel(
                    s.getId(),
                    panelIndex
            );
        } else {
            throw new IllegalStateException(
                    "Unhandled action: " + column.getAction()
            );
        }

    }

    // resolving cell text based on value key
    @Override
    public String resolveText(TableColumn column, Specimen s) {

        if (column instanceof CommandLinkColumn linkColumn) {

            if ("fullIdentifier".equals(linkColumn.getValueKey())) {
                return s.displayFullIdentifier();
            }

            throw new IllegalStateException(
                    "Unknown valueKey: " + linkColumn.getValueKey()
            );
        }

        return "";
    }

    @Override
    public Integer resolveCount(TableColumn column, Specimen s) {
        return null;
    }

    @Override
    public boolean isRendered(TableColumn column, String key, Specimen s, Integer panelIndex) {
        return switch (key) {
            case "writeMode" -> flowBean.getIsWriteMode();
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

                // Duplicate row (RecordingUnit only)
                RowAction.builder()
                        .action(DUPLICATE_ROW)
                        .processExpr("@this")
                        .updateSelfTable(true) // <-- mettra à jour :#{cc.clientId}:entityDatatable
                        .styleClass("sia-icon-btn")
                        .build()
        );
    }


    @Override
    public void handleRelationAction(RelationColumn col, Specimen s, Integer panelIndex, TableColumnAction action) {
        throw new IllegalStateException(
                "Unhandled relation action: " + action
        );

    }

    public boolean isRendered(RowAction action, Specimen s) {
        return switch (action.getAction()) {
            case DUPLICATE_ROW -> flowBean.getIsWriteMode();
            case TOGGLE_BOOKMARK -> true;
            default -> true;
        };
    }


    public String resolveIcon(RowAction action, Specimen s) {
        return switch (action.getAction()) {
            case TOGGLE_BOOKMARK -> Boolean.TRUE.equals(navBean.isSpecimenBookmarkedByUser(s.getFullIdentifier()))
                            ? "bi bi-bookmark-x-fill"
                            : "bi bi-bookmark-plus";
            case DUPLICATE_ROW -> "bi bi-copy";
            default -> "";
        };
    }

    public void handleRowAction(RowAction action, Specimen s) {
        if (action.getAction() == DUPLICATE_ROW) {
            specimenLazyDataModel.duplicateRow();
        } else {
            throw new IllegalStateException("Unhandled action: " + action.getAction());
        }
    }

    public void handleRowAction(RowAction action, TreeNode<Specimen> node) {
        handleRowAction(action, node.getData());
    }

    @Override
    public TreeNode<RecordingUnit> getTreeRoot() {
        return null;
    }

    @Override
    public void save() {
        // will be implemented when working on specimen table
    }

}
