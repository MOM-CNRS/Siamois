package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecordingUnitHandler implements INewUnitHandler<RecordingUnit> {

    private final RecordingUnitService recordingUnitService;
    private final ActionUnitService actionUnitService;
    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;

    public RecordingUnitHandler(RecordingUnitService recordingUnitService, ActionUnitService actionUnitService, SessionSettingsBean sessionSettingsBean, LangBean langBean) {
        this.recordingUnitService = recordingUnitService;
        this.actionUnitService = actionUnitService;
        this.sessionSettingsBean = sessionSettingsBean;
        this.langBean = langBean;
    }

    @Override
    public List<SpatialUnit> getSpatialUnitOptions(RecordingUnit unit) {
        ActionUnit actionUnit = unit.getActionUnit();
        // Return the spatial context of the parent action
        if (actionUnit != null) {
            return new ArrayList<>(actionUnit.getSpatialContext());
        }

        return List.of();
    }

    @Override public UnitKind kind() { return UnitKind.RECORDING; }
    @Override public RecordingUnit newEmpty() {
        RecordingUnit recordingUnit = new RecordingUnit();
        recordingUnit.setOpeningDate(OffsetDateTime.now());
        return recordingUnit;
    }
    @Override public RecordingUnit save(UserInfo u, RecordingUnit unit) throws EntityAlreadyExistsException {
        return recordingUnitService.save(unit, unit.getType(), null, null, null); }
    @Override public String dialogWidgetVar() { return "newUnitDiag"; }

    @Override public void initFromContext(GenericNewUnitDialogBean<?> bean) throws CannotInitializeNewUnitDialogException {

        RecordingUnit unit = (RecordingUnit) bean.getUnit();
        NewUnitContext ctx = bean.getNewUnitContext();
        if (ctx == null) throw new CannotInitializeNewUnitDialogException("Recording unit cannot be created without a context");

        // 1) If creation comes from toolbar: use SCOPE
        NewUnitContext.Trigger trigger = ctx.getTrigger();
        if (trigger != null && trigger.getType() == NewUnitContext.TriggerType.TOOLBAR) {
            applyScope(unit, ctx);
            return;
        }

        throw new CannotInitializeNewUnitDialogException("Recording unit cannot be created without a context");

    }

    private void applyScope(RecordingUnit unit, NewUnitContext ctx) throws CannotInitializeNewUnitDialogException {
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope == null || scope.getKey() == null || scope.getEntityId() == null) {
            throw new CannotInitializeNewUnitDialogException("Recording unit cannot be created without a context");
        }


        if ("ACTION".equals(scope.getKey())) {
            ActionUnit au = actionUnitService.findById(scope.getEntityId()); // adapt Optional
            if (au != null) {
                unit.setCreatedByInstitution(au.getCreatedByInstitution());
                unit.setActionUnit(au);
                unit.setAuthor(sessionSettingsBean.getAuthenticatedUser());
                unit.setContributors(List.of(sessionSettingsBean.getAuthenticatedUser()));
                unit.setOpeningDate(OffsetDateTime.now());
                return ;
            }
        }

        throw new CannotInitializeNewUnitDialogException("Recording unit cannot be created without a context");
    }


    @Override
    public String getTitle(NewUnitContext ctx) {
        if (ctx == null) {
            return INewUnitHandler.super.getTitle(ctx);
        }


        // =========================
        // 2) TOOLBAR trigger (scope table)
        // =========================
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope != null && "ACTION".equals(scope.getKey()) && scope.getEntityId() != null) {

            ActionUnit scoped = actionUnitService.findById(scope.getEntityId());
            String name = scoped != null ? scoped.getName() : ("#" + scope.getEntityId());

            return langBean.msg("dialog.label.title.recording.actionContext",name);
        }

        // =========================
        // 3) Default fallback
        // =========================
        return INewUnitHandler.super.getTitle(ctx);
    }

    @Override
    public String getName(RecordingUnit unit) {
        return unit.getFullIdentifier();
    }


}
