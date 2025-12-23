package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ActionUnitHandler implements INewUnitHandler<ActionUnit> {

    private final ActionUnitService actionUnitService;
    private final SpatialUnitService spatialUnitService;
    private final LangBean langBean;

    public ActionUnitHandler(ActionUnitService actionUnitService, SpatialUnitService spatialUnitService, LangBean langBean) {
        this.actionUnitService = actionUnitService;
        this.spatialUnitService = spatialUnitService;
        this.langBean = langBean;
    }

    @Override
    public UnitKind kind() {
        return UnitKind.ACTION;
    }

    @Override
    public ActionUnit newEmpty() {
        return new ActionUnit();
    }

    @Override
    public ActionUnit save(UserInfo u, ActionUnit unit) throws EntityAlreadyExistsException {
        return actionUnitService.save(u, unit, unit.getType());
    }

    @Override
    public String dialogWidgetVar() {
        return "newUnitDiag";
    }

    @Override
    public void initFromContext(GenericNewUnitDialogBean<?> bean) {
        ActionUnit unit = (ActionUnit) bean.getUnit();
        NewUnitContext ctx = bean.getNewUnitContext();
        if (ctx == null) return;

        // 1) If creation comes from toolbar: use SCOPE
        NewUnitContext.Trigger trigger = ctx.getTrigger();
        if (trigger != null && trigger.getType() == NewUnitContext.TriggerType.TOOLBAR) {
            applyScope(unit, ctx);
            return;
        }

        // 2) If creation comes from a cell: use TRIGGER
        if (trigger == null || trigger.getType() != NewUnitContext.TriggerType.CELL) return;

        Long clickedId = trigger.getClickedId();
        String key = trigger.getColumnKey();
        UnitKind clickedKind = trigger.getClickedKind();

        if (clickedId == null || key == null || clickedKind == null) return;

        switch (key) {
            case "related_actions" -> {
                if (clickedKind == UnitKind.SPATIAL) {
                    SpatialUnit clickedSpatial = spatialUnitService.findById(clickedId); // adapt Optional
                    if (clickedSpatial != null) {
                        unit.getSpatialContext().add(clickedSpatial);
                    }
                }
            }
            default -> {
                // better to no-op than crash
            }
        }
    }

    private void applyScope(ActionUnit unit, NewUnitContext ctx) {
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope == null || scope.getKey() == null || scope.getEntityId() == null) {
            return;
        }

        // Example: toolbar on a "Actions of SpatialUnit" screen
        if ("SPATIAL".equals(scope.getKey())) {
            SpatialUnit su = spatialUnitService.findById(scope.getEntityId()); // adapt Optional
            if (su != null) {
                unit.getSpatialContext().add(su);
            }
        }
    }

    
    @Override
    public List<SpatialUnit> getSpatialUnitOptions(ActionUnit unit) {
        return List.of();
    }

    @Override
    public String getName(ActionUnit unit) {
        return unit.getName();
    }

    @Override
    public String getTitle(NewUnitContext ctx) {
        if (ctx == null) {
            return INewUnitHandler.super.getTitle(ctx);
        }

        // ======================
        // 1) CELL-triggered creation
        // ======================
        NewUnitContext.Trigger trigger = ctx.getTrigger();
        if (trigger != null && trigger.getType() == NewUnitContext.TriggerType.CELL) {

            Long clickedId = trigger.getClickedId();
            String columnKey = trigger.getColumnKey();
            UnitKind clickedKind = trigger.getClickedKind();

            if (clickedId != null && columnKey != null && clickedKind == UnitKind.SPATIAL) {
                SpatialUnit clicked = spatialUnitService.findById(clickedId);
                String name = clicked != null ? clicked.getName() : ("#" + clickedId);

                if ("related_actions".equals(columnKey)) {
                    return langBean.msg("dialog.label.title.action.spatialContext") + " " + name;
                }
            }
        }

        // ======================
        // 2) TOOLBAR-triggered creation (use scope)
        // ======================
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope != null && "SPATIAL".equals(scope.getKey()) && scope.getEntityId() != null) {

            SpatialUnit scoped = spatialUnitService.findById(scope.getEntityId());
            String name = scoped != null ? scoped.getName() : ("#" + scope.getEntityId());

            return langBean.msg("dialog.label.title.action.spatialContext") + " " + name;
        }

        // ======================
        // 3) Default fallback
        // ======================
        return INewUnitHandler.super.getTitle(ctx);
    }



}
