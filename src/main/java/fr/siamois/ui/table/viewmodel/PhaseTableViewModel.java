package fr.siamois.ui.table.viewmodel;

import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.BasePhaseLazyDataModel;
import fr.siamois.ui.table.RowAction;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.column.RelationColumn;
import fr.siamois.ui.table.column.TableColumn;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

import java.util.List;

import static fr.siamois.ui.table.column.TableColumnAction.GO_TO_PHASE;

@Getter
public class PhaseTableViewModel extends EntityTableViewModel<PhaseDTO, Long> {

    private final BasePhaseLazyDataModel phaseLazyDataModel;
    private final FlowBean flowBean;
    private final InstitutionService institutionService;
    private final SessionSettingsBean sessionSettingsBean;
    private final PhaseService phaseService;

    public PhaseTableViewModel(BasePhaseLazyDataModel phaseLazyDataModel,
                               FormService formService,
                               SessionSettingsBean sessionSettingsBean,
                               SpatialUnitTreeService spatialUnitTreeService,
                               SpatialUnitService spatialUnitService,
                               NavBean navBean,
                               FlowBean flowBean,
                               GenericNewUnitDialogBean<PhaseDTO> genericNewUnitDialogBean,
                               InstitutionService institutionService,
                               FormContextServices formContextServices,
                               PhaseService phaseService) {
        super(
                phaseLazyDataModel,
                genericNewUnitDialogBean,
                formService,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                sessionSettingsBean.getLangBean(),
                PhaseDTO::getId,
                "type",
                formContextServices
        );
        this.phaseLazyDataModel = phaseLazyDataModel;
        this.sessionSettingsBean = sessionSettingsBean;
        this.flowBean = flowBean;
        this.institutionService = institutionService;
        this.phaseService = phaseService;
    }

    @Override
    protected FormUiDto resolveRowFormFor(PhaseDTO phase) {
        return null;
    }

    @Override
    protected void configureRowSystemFields(PhaseDTO phase, FormUiDto rowForm) {
        // no system fields to configure
    }

    @Override
    protected void handleCommandLink(CommandLinkColumn column, PhaseDTO phase) {
        if (column.getAction() == GO_TO_PHASE) {
            setOverviewEntityId(phase.getId());
            flowBean.addPhaseToOverview(phase.getId(), parentPanel, null, false);
        } else {
            throw new IllegalStateException("Unhandled action: " + column.getAction());
        }
    }

    @Override
    public String resolveText(TableColumn column, PhaseDTO phase) {
        if (column instanceof CommandLinkColumn linkColumn) {
            if ("identifier".equals(linkColumn.getValueKey())) return phase.getIdentifier();
            throw new IllegalStateException("Unknown valueKey: " + linkColumn.getValueKey());
        }
        return "";
    }

    @Override
    public void handleLinkEdit(CommandLinkColumn column, PhaseDTO item, String newValue) {
        String trimmed = newValue == null ? "" : newValue.trim();
        if (trimmed.isEmpty()) {
            MessageUtils.displayWarnMessage(langBean, "phase.error.identifier.blank");
            return;
        }

        String previous = item.getIdentifier();
        item.setIdentifier(trimmed);

        if (phaseService.identifierAlreadyExistInAction(item)) {
            item.setIdentifier(previous);
            MessageUtils.displayWarnMessage(langBean, "phase.error.identifier.alreadyExists");
            return;
        }

        try {
            phaseService.save(item);
        } catch (RuntimeException e) {
            item.setIdentifier(previous);
            MessageUtils.displayErrorMessage(sessionSettingsBean.getLangBean(), "common.entity.phase.updateFailed", item.getIdentifier());
        }
    }

    @Override
    public Integer resolveCount(TableColumn column, PhaseDTO phase) {
        return 0;
    }

    @Override
    public boolean isRendered(TableColumn column, String key, PhaseDTO phase) {
        return switch (key) {
            case "writeMode" -> flowBean.getIsWriteMode();
            default -> false;
        };
    }

    @Override
    public List<RowAction> getRowActions() {
        return List.of(
                RowAction.builder()
                        .action(TableColumnAction.TOGGLE_BOOKMARK)
                        .processExpr("@this")
                        .updateExpr("bookmarkToggleButton, subSidebarForm")
                        .updateSelfTable(false)
                        .styleClass("sia-icon-btn")
                        .build()
        );
    }

    @Override
    public void handleRelationAction(RelationColumn col, PhaseDTO phase, TableColumnAction action) {
        throw new IllegalStateException("Unhandled relation action: " + action);
    }

    public boolean isRendered(RowAction action, PhaseDTO phase) {
        return switch (action.getAction()) {
            case TOGGLE_BOOKMARK -> false;
            default -> true;
        };
    }

    public String resolveIcon(RowAction action, PhaseDTO phase) {
        return "";
    }

    @Override
    public boolean canUserEditRow(PhaseDTO unit) {
        return true;
    }

    @Override
    public BaseLazyDataModel<PhaseDTO> getLazyDataModel() {
        return phaseLazyDataModel;
    }

    @Override
    protected boolean unitIsLeaf(@NonNull PhaseDTO unit) {
        return true;
    }

    @Override
    protected @NonNull List<PhaseDTO> loadChildrensOfUnit(@NonNull PhaseDTO parentUnit) {
        return List.of();
    }

    @Override
    public boolean isTreeViewSupported() {
        return false;
    }
}
