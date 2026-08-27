package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PhaseHandler implements INewUnitHandler<PhaseDTO> {

    private final PhaseService phaseService;
    private final SessionSettingsBean sessionSettingsBean;
    private final ActionUnitService actionUnitService;

    public PhaseHandler(PhaseService phaseService, SessionSettingsBean sessionSettingsBean,
                        ActionUnitService actionUnitService) {
        this.phaseService = phaseService;
        this.sessionSettingsBean = sessionSettingsBean;
        this.actionUnitService = actionUnitService;
    }

    @Override
    public UnitKind kind() {
        return UnitKind.PHASE;
    }

    @Override
    public PhaseDTO newEmpty() {
        return new PhaseDTO();
    }

    @Override
    public PhaseDTO save(UserInfo user, PhaseDTO unit) throws EntityAlreadyExistsException {
        return phaseService.save(unit);
    }

    @Override
    public String dialogWidgetVar() {
        return "newUnitDiag";
    }

    @Override
    public void initFromContext(GenericNewUnitDialogBean<?> bean) throws CannotInitializeNewUnitDialogException {
        PhaseDTO unit = (PhaseDTO) bean.getUnit();
        NewUnitContext ctx = bean.getNewUnitContext();
        if (ctx == null) return;
        unit.setCreatedBy(sessionSettingsBean.getAuthenticatedUser());
        unit.setCreatedByInstitution(sessionSettingsBean.getSelectedInstitution());
        applyActionScope(unit, ctx);
    }

    private void applyActionScope(PhaseDTO unit, NewUnitContext ctx) throws CannotInitializeNewUnitDialogException {
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope == null || !"ACTION".equals(scope.getKey()) || scope.getEntityId() == null) return;
        ActionUnitDTO actionUnit = actionUnitService.findById(scope.getEntityId());
        if (actionUnit == null) throw new CannotInitializeNewUnitDialogException("Action unit not found");
        unit.setActionUnit(new ActionUnitSummaryDTO(actionUnit));
    }

    @Override
    public String getName(PhaseDTO unit) {
        return unit.getIdentifier() != null ? unit.getIdentifier() : "";
    }

    @Override
    public List<SpatialUnitSummaryDTO> getSpatialUnitOptions(PhaseDTO unit) {
        return List.of();
    }
}
