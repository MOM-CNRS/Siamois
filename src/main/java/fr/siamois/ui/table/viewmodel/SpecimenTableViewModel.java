package fr.siamois.ui.table.viewmodel;

import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.BaseSpecimenLazyDataModel;
import fr.siamois.ui.table.RowAction;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.column.RelationColumn;
import fr.siamois.ui.table.column.TableColumn;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.primefaces.model.TreeNode;

import java.util.ArrayList;
import java.util.List;

import static fr.siamois.ui.table.column.TableColumnAction.DUPLICATE_ROW;
import static fr.siamois.ui.table.column.TableColumnAction.GO_TO_SPECIMEN;

/**
 * View model spécifique pour les tableaux de Specimen.
 *
 * - spécialise EntityTableViewModel pour T = Specimen, ID = Long
 * - implémente :
 *      - resolveRowFormFor
 *      - configureRowSystemFields
 */
@Getter
public class SpecimenTableViewModel extends EntityTableViewModel<SpecimenDTO, Long> {

    private final BaseSpecimenLazyDataModel specimenLazyDataModel;
    private final FlowBean flowBean;
    private final SpecimenService specimenService;
    private final ProfilePermissionService profilePermissionService;


    private final SessionSettingsBean sessionSettingsBean;

    public SpecimenTableViewModel(BaseSpecimenLazyDataModel lazyDataModel,
                                  FormService formService,
                                  SessionSettingsBean sessionSettingsBean,
                                  SpatialUnitTreeService spatialUnitTreeService,
                                  SpatialUnitService spatialUnitService,
                                  NavBean navBean,
                                  FlowBean flowBean,
                                  SpecimenService specimenService,
                                  ProfilePermissionService profilePermissionService,
                                  GenericNewUnitDialogBean<SpecimenDTO> genericNewUnitDialogBean, FormContextServices formContextServices) {

        super(
                lazyDataModel,
                genericNewUnitDialogBean,
                formService,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                SpecimenDTO::getId,   // idExtractor
                "type"   ,// formScopeValueBinding
                sessionSettingsBean.getLangBean(),
                formContextServices
        );
        this.specimenLazyDataModel = lazyDataModel;
        this.setTreeMode(false);
        this.setSwitchVisible(false);
        this.sessionSettingsBean = sessionSettingsBean;
        this.flowBean = flowBean;
        this.specimenService = specimenService;
        this.profilePermissionService = profilePermissionService;


    }

    @Override
    protected FormUiDto resolveRowFormFor(SpecimenDTO s) {
        return null;
    }

    @Override
    protected void configureRowSystemFields(SpecimenDTO s, FormUiDto rowForm) {
        // no specific configs
    }

    @Override
    protected void handleCommandLink(CommandLinkColumn column,
                                     SpecimenDTO s) {

        if (column.getAction() == GO_TO_SPECIMEN) {
            setOverviewEntityId(s.getId());
            flowBean.addSpecimenToOverview(s.getId(), parentPanel, null, false);
        } else {
            throw new IllegalStateException(
                    "Unhandled action: " + column.getAction()
            );
        }

    }

    // resolving cell text based on value key
    @Override
    public String resolveText(TableColumn column, SpecimenDTO s) {

        if (column instanceof CommandLinkColumn linkColumn) {

            if ("fullIdentifier".equals(linkColumn.getValueKey())) {
                return s.getFullIdentifier();
            }

            throw new IllegalStateException(
                    "Unknown valueKey: " + linkColumn.getValueKey()
            );
        }

        return "";
    }

    @Override
    public void handleLinkEdit(CommandLinkColumn column, SpecimenDTO item, String newValue) {
        String trimmed = newValue == null ? "" : newValue.trim();
        if (trimmed.isEmpty()) {
            MessageUtils.displayWarnMessage(langBean, "specimen.error.identifier.blank");
            return;
        }

        String previous = item.getFullIdentifier();
        item.setFullIdentifier(trimmed);

        if (specimenService.fullIdentifierAlreadyExistInAction(item)) {
            item.setFullIdentifier(previous);
            MessageUtils.displayWarnMessage(langBean, "specimen.error.identifier.alreadyExists");
            return;
        }

        try {
            specimenService.save(item);
        } catch (RuntimeException e) {
            item.setFullIdentifier(previous);
            MessageUtils.displayErrorMessage(sessionSettingsBean.getLangBean(), "common.entity.specimen.updateFailed", item.getFullIdentifier());
        }
    }

    @Override
    public Integer resolveCount(TableColumn column, SpecimenDTO s) {
        return null;
    }

    @Override
    public boolean isRendered(TableColumn column, String key, SpecimenDTO s) {
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
                        .updateExpr("bookmarkToggleButton, subSidebarForm")
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
    public void handleRelationAction(RelationColumn col, SpecimenDTO s, TableColumnAction action) {
        throw new IllegalStateException(
                "Unhandled relation action: " + action
        );

    }

    public boolean isRendered(RowAction action, SpecimenDTO s) {
        if (action.getAction() == TableColumnAction.TOGGLE_BOOKMARK) {
            return true;
        }
        return canUserEditRow(s);
    }


    public String resolveIcon(RowAction action, SpecimenDTO s) {
        return switch (action.getAction()) {
            case TOGGLE_BOOKMARK -> Boolean.TRUE.equals(navBean.isSpecimenBookmarkedByUser(s.getFullIdentifier()))
                            ? "bi bi-bookmark-x-fill"
                            : "bi bi-bookmark";
            case DUPLICATE_ROW -> "bi bi-copy";
            default -> "";
        };
    }

    public void handleRowAction(RowAction action, SpecimenDTO s) {
        if (action.getAction() == DUPLICATE_ROW) {
            SpecimenDTO created = specimenLazyDataModel.duplicateRow();
            if (created != null && created.getId() != null) {
                markRecentlyCreated(java.util.List.of(created.getId()));
            }
        } else {
            throw new IllegalStateException("Unhandled action: " + action.getAction());
        }
    }

    public void handleRowAction(RowAction action, TreeNode<SpecimenDTO> node) {
        handleRowAction(action, node.getData());
    }

    @Override
    public TreeNode<RecordingUnit> getTreeRoot() {
        return null;
    }


    @Override
    public boolean canUserEditRow(SpecimenDTO unit) {
        Long actionUnitId = unit.getActionUnit() != null ? unit.getActionUnit().getId() : null;
        return canEditByActionUnit(profilePermissionService, sessionSettingsBean.getUserInfo(),
                PermissionConstants.PROJECT_EDIT_FINDS, PermissionConstants.PROJECT_EDIT_FINDS,
                s -> s.getActionUnit() != null ? s.getActionUnit().getId() : null, actionUnitId);
    }

    @Override
    public BaseLazyDataModel<SpecimenDTO> getLazyDataModel() {
        return specimenLazyDataModel;
    }

    @Override
    protected boolean unitIsLeaf(@NonNull SpecimenDTO unit) {
        return true;
    }

    @Override
    protected @NonNull List<SpecimenDTO> loadChildrensOfUnit(@NonNull SpecimenDTO parentUnit) {
        return new ArrayList<>();
    }

}
