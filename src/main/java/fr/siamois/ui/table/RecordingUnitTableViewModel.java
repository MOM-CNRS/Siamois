package fr.siamois.ui.table;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.CustomFieldInteger;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.authorization.writeverifier.RecordingUnitWriteVerifier;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.lazydatamodel.BaseRecordingUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.tree.RecordingUnitTreeTableLazyModel;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import org.primefaces.model.TreeNode;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static fr.siamois.ui.bean.dialog.newunit.NewUnitContext.TreeInsert.ROOT;
import static fr.siamois.ui.table.TableColumnAction.DUPLICATE_ROW;
import static fr.siamois.ui.table.TableColumnAction.GO_TO_RECORDING_UNIT;

/**
 * View model spécifique pour les tableaux de RecordingUnit.
 *
 * - spécialise EntityTableViewModel pour T = RecordingUnit, ID = Long
 * - implémente :
 *      - resolveRowFormFor
 *      - configureRowSystemFields
 */
@Getter
public class RecordingUnitTableViewModel extends EntityTableViewModel<RecordingUnit, Long> {

    public static final String THIS = "@this";
    public static final String SIA_ICON_BTN = "sia-icon-btn";
    /** Lazy model spécifique RecordingUnit (accès à selectedUnits, etc.) */
    private final BaseRecordingUnitLazyDataModel recordingUnitLazyDataModel;
    private final FlowBean flowBean;
    private final RecordingUnitService recordingUnitService;

    private final RecordingUnitWriteVerifier recordingUnitWriteVerifier;

    private final SessionSettingsBean sessionSettingsBean;

    public RecordingUnitTableViewModel(BaseRecordingUnitLazyDataModel lazyDataModel,
                                       FormService formService,
                                       SessionSettingsBean sessionSettingsBean,
                                       SpatialUnitTreeService spatialUnitTreeService,
                                       SpatialUnitService spatialUnitService,
                                       NavBean navBean,
                                       FlowBean flowBean, GenericNewUnitDialogBean<RecordingUnit> genericNewUnitDialogBean,
                                       RecordingUnitWriteVerifier recordingUnitWriteVerifier,
                                       RecordingUnitService recordingUnitService,
                                       RecordingUnitTreeTableLazyModel treeLazyModel) {

        super(
                lazyDataModel,
                treeLazyModel,
                genericNewUnitDialogBean,
                formService,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                RecordingUnit::getId,   // idExtractor
                "type"                  // formScopeValueBinding
        );
        this.recordingUnitLazyDataModel = lazyDataModel;
        this.sessionSettingsBean = sessionSettingsBean;
        this.flowBean = flowBean;
        this.recordingUnitService = recordingUnitService;
        this.recordingUnitWriteVerifier = recordingUnitWriteVerifier;

    }

    @Override
    protected CustomForm resolveRowFormFor(RecordingUnit ru) {
        Concept type = ru.getType();
        if (type == null) {
            return null;
        }
        return formService.findCustomFormByRecordingUnitTypeAndInstitutionId(
                type,
                sessionSettingsBean.getSelectedInstitution()
        );
    }

    @Override
    protected void configureRowSystemFields(RecordingUnit ru, CustomForm rowForm) {
        if (rowForm == null || rowForm.getLayout() == null) {
            return;
        }

        for (CustomField field : getAllFieldsFromForm(rowForm)) {
            configureIdentifierField(ru, field);
            configureDateTimeField(ru, field);
        }
    }

    private void configureIdentifierField(RecordingUnit ru, CustomField field) {
        if (!"identifier".equals(field.getValueBinding()) || !(field instanceof CustomFieldInteger cfi)) {
            return;
        }
        if (ru.getActionUnit() != null) {
            cfi.setMaxValue(ru.getActionUnit().getMaxRecordingUnitCode());
            cfi.setMinValue(ru.getActionUnit().getMinRecordingUnitCode());
        }
    }

    private void configureDateTimeField(RecordingUnit ru, CustomField field) {
        if (!(field instanceof CustomFieldDateTime dt)) {
            return;
        }

        if ("openingDate".equals(field.getValueBinding()) && ru.getClosingDate() != null) {
            dt.setMax(ru.getClosingDate().toLocalDateTime());
            dt.setMin(LocalDateTime.of(1000, Month.JANUARY, 1, 1, 1));
        } else if ("closingDate".equals(field.getValueBinding()) && ru.getOpeningDate() != null) {
            dt.setMin(ru.getOpeningDate().toLocalDateTime());
            dt.setMax(LocalDateTime.of(9999, Month.DECEMBER, 31, 23, 59));
        }
    }


    @Override
    protected void handleCommandLink(CommandLinkColumn column,
                                     RecordingUnit ru,
                                     Integer panelIndex) {

        if (column.getAction() == GO_TO_RECORDING_UNIT) {
            flowBean.goToRecordingUnitByIdNewPanel(
                    ru.getId(),
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
    public String resolveText(TableColumn column, RecordingUnit ru) {

        if (column instanceof CommandLinkColumn linkColumn) {
            if ("fullIdentifier".equals(linkColumn.getValueKey())) {
                return ru.displayFullIdentifier();
            } else {
                throw new IllegalStateException(
                        "Unknown valueKey: " + linkColumn.getValueKey()
                );
            }
        }

        return "";
    }

    @Override
    public Integer resolveCount(TableColumn column, RecordingUnit ru) {
        if (column instanceof RelationColumn rel) {
            return switch (rel.getCountKey()) {
                case "parents" -> ru.getParents() == null ? 0 : ru.getParents().size();
                case "children" -> ru.getChildren() == null ? 0 : ru.getChildren().size();
                case "specimenList" -> ru.getSpecimenList() == null ? 0 : ru.getSpecimenList().size();
                default -> 0;
            };
        }
        return 0;
    }

    @Override
    public boolean isRendered(TableColumn column, String key, RecordingUnit ru, Integer panelIndex) {
        return switch (key) {
            case "writeMode" -> flowBean.getIsWriteMode();
            case "recordingUnitCreateAllowed" -> recordingUnitWriteVerifier.hasSpecificWritePermission(flowBean.getSessionSettings().getUserInfo(), ru);
            case "specimenCreateAllowed" -> recordingUnitWriteVerifier.hasSpecificWritePermission(flowBean.getSessionSettings().getUserInfo(), ru);
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
                        .updateExpr("bookmarkToggleButton navBarCsrfForm:siamoisNavForm:bookmarkGroup")
                        .updateSelfTable(false)
                        .styleClass(SIA_ICON_BTN)
                        .build(),

                // Duplicate row (RecordingUnit only)
                RowAction.builder()
                        .action(TableColumnAction.DUPLICATE_ROW)
                        .processExpr(THIS)
                        .updateSelfTable(true) // <-- mettra à jour :#{cc.clientId}:entityDatatable
                        .styleClass(SIA_ICON_BTN)
                        .build(),

                // Add children
                RowAction.builder()
                        .action(TableColumnAction.NEW_CHILDREN)
                        .processExpr(THIS)
                        .updateSelfTable(true)
                        .styleClass(SIA_ICON_BTN)
                        .build()
        );
    }


    @Override
    public void handleRelationAction(RelationColumn col, RecordingUnit ru, Integer panelIndex, TableColumnAction action) {
        switch (action) {

            case VIEW_RELATION ->
                    flowBean.goToRecordingUnitByIdNewPanel(ru.getId(), panelIndex, col.getViewTargetIndex());

            case ADD_RELATION -> {
                // Dispatch based on column.countKey (or add a dedicated "relationKey")
                // handle adding parent, children or specimen
            }

            default -> throw new IllegalStateException("Unhandled relation action: " + action);
        }
    }

    public boolean isRendered(RowAction action, RecordingUnit ru) {
        return switch (action.getAction()) {
            case DUPLICATE_ROW -> flowBean.getIsWriteMode();
            case TOGGLE_BOOKMARK -> true;
            case NEW_ACTION -> flowBean.getIsWriteMode();
            default -> true;
        };
    }


    public String resolveIcon(RowAction action,RecordingUnit ru) {

        return switch (action.getAction()) {
            case TOGGLE_BOOKMARK -> Boolean.TRUE.equals(navBean.isRecordingUnitBookmarkedByUser(ru.getFullIdentifier()))
                            ? "bi bi-bookmark-x-fill"
                            : "bi bi-bookmark-plus";
            case DUPLICATE_ROW -> "bi bi-copy";
            case NEW_CHILDREN -> "bi bi-node-plus-fill rotate-90";
            default -> "";
        };
    }
    public void handleRowAction(RowAction action,  RecordingUnit ru) {
        switch (action.getAction()) {
            case TOGGLE_BOOKMARK -> navBean.toggleRecordingUnitBookmark(ru);
            case DUPLICATE_ROW -> this.duplicateRow(ru, null);
            case NEW_CHILDREN -> {
                // Open new rec unit dialog
                // The new spatial rec will be children of the current ru
                NewUnitContext ctx = NewUnitContext.builder()
                        .kindToCreate(UnitKind.RECORDING)
                        .trigger(NewUnitContext.Trigger.cell(UnitKind.RECORDING, ru.getId(), "children"))
                        .insertPolicy(NewUnitContext.UiInsertPolicy.builder()
                                .listInsert(NewUnitContext.ListInsert.TOP)
                                .treeInsert(NewUnitContext.TreeInsert.CHILD_FIRST)
                                .build())
                        .build();

                openCreateDialog(ctx, genericNewUnitDialogBean);
            }
            default -> throw new IllegalStateException("Unhandled action: " + action.getAction());
        }
    }

    // actions specific to treetable
    public void handleRowAction(RowAction action, TreeNode<RecordingUnit> node) {
        RecordingUnit ru = node.getData();
        if (action.getAction() == DUPLICATE_ROW) {
            if (!Objects.equals(node.getParent().getRowKey(), "root")) {
                this.duplicateRow(node.getData(), node.getParent().getData());
                return;
            }
            this.duplicateRow(node.getData(), null);
        } else {
            handleRowAction(action, ru);
        }
    }

    @Override
    public boolean isTreeViewSupported() {
        return true;
    }

    @Override
    public void save() {
// will be implemented when working on recording unit table
    }

    @Override
    public boolean canUserEditRow(RecordingUnit unit) {
        return true; // todo: implement permission
    }

    @Override
    public TreeNode<RecordingUnit> getTreeRoot() {
        return treeLazyModel.getRoot();
    }

    // Duplique une unité d'enregistrement
    // Le place au même niveau dans la hierarchie mais ne copie pas les enfants
    private void duplicateRow(RecordingUnit toDuplicate, RecordingUnit parent) {

        // Create a copy from selected row
        RecordingUnit newUnit = new RecordingUnit(toDuplicate);

        if(parent != null) {
            newUnit.getParents().add(parent);
        }

        newUnit.setIdentifier(recordingUnitService.generateNextIdentifier(newUnit));

        newUnit = recordingUnitService.save(newUnit, newUnit.getType(), List.of(),  List.of(),  List.of());

        // Build the creation context (as child of the parent of the duplicated row, or root if no parent)
        NewUnitContext ctx = NewUnitContext.builder()
                .kindToCreate(UnitKind.RECORDING)
                .trigger(
                        parent == null
                                ? null
                                : NewUnitContext.Trigger.cell(
                                UnitKind.RECORDING,
                                parent.getId(),
                                "parents"
                        )
                )
                .insertPolicy(NewUnitContext.UiInsertPolicy.builder()
                        .listInsert(NewUnitContext.ListInsert.TOP)
                        .treeInsert(parent == null ? ROOT : NewUnitContext.TreeInsert.CHILD_FIRST)
                        .build())
                .build();


        onAnyEntityCreated(newUnit, ctx) ;
        MessageUtils.displayInfoMessage(sessionSettingsBean.getLangBean(), "common.action.duplicateEntity", toDuplicate.getFullIdentifier());
    }

}
