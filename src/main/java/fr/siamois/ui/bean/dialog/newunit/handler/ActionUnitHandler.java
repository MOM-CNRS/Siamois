package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ActionUnitHandler implements INewUnitHandler<ActionUnitDTO> {

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
    public ActionUnitDTO newEmpty() {
        return new ActionUnitDTO();
    }

    @Override
    public ActionUnitDTO save(UserInfo u, ActionUnitDTO unit) throws EntityAlreadyExistsException {
        return actionUnitService.save(u, unit, unit.getType());
    }

    @Override
    public String dialogWidgetVar() {
        return "newUnitDiag";
    }

    @Override
    public void initFromContext(GenericNewUnitDialogBean<?> bean) {
        ActionUnitDTO unit = (ActionUnitDTO) bean.getUnit();
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

        if ("related_actions".equals(key) && clickedKind == UnitKind.SPATIAL) {
            SpatialUnitDTO clickedSpatial = spatialUnitService.findById(clickedId); // adapt Optional
            if (clickedSpatial != null) {
                unit.getSpatialContext().add(new SpatialUnitSummaryDTO(clickedSpatial));
            }
        }

    }

    private void applyScope(ActionUnitDTO unit, NewUnitContext ctx) {
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope == null || scope.getKey() == null || scope.getEntityId() == null) {
            return;
        }

        // Example: toolbar on a "Actions of SpatialUnit" screen
        if ("SPATIAL".equals(scope.getKey())) {
            SpatialUnitDTO su = spatialUnitService.findById(scope.getEntityId()); // adapt Optional
            if (su != null) {
                unit.getSpatialContext().add(new SpatialUnitSummaryDTO(su));
            }
        }
    }


    @Override
    public List<SpatialUnitSummaryDTO> getSpatialUnitOptions(ActionUnitDTO unit) {
        return List.of();
    }

    @Override
    public String getName(ActionUnitDTO unit) {
        return unit.getName();
    }

    @Override
    public String getTitle(NewUnitContext ctx) {
        if (ctx == null) {
            return INewUnitHandler.super.getTitle(ctx);
        }

        String title = getSpatialContextTitleFromCellTrigger(ctx);
        if (title != null) {
            return title;
        }

        title = getSpatialContextTitleFromScope(ctx);
        if (title != null) {
            return title;
        }

        return INewUnitHandler.super.getTitle(ctx);
    }

    private String getSpatialContextTitleFromCellTrigger(NewUnitContext ctx) {
        NewUnitContext.Trigger trigger = ctx.getTrigger();
        if (trigger == null || trigger.getType() != NewUnitContext.TriggerType.CELL) {
            return null;
        }

        if (trigger.getClickedId() == null
                || trigger.getColumnKey() == null
                || trigger.getClickedKind() != UnitKind.SPATIAL
                || !"related_actions".equals(trigger.getColumnKey())) {
            return null;
        }

        return buildSpatialContextTitle(trigger.getClickedId());
    }

    private String getSpatialContextTitleFromScope(NewUnitContext ctx) {
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope == null || scope.getEntityId() == null || !"SPATIAL".equals(scope.getKey())) {
            return null;
        }

        return buildSpatialContextTitle(scope.getEntityId());
    }

    private String buildSpatialContextTitle(Long spatialUnitId) {
        SpatialUnitDTO unit = spatialUnitService.findById(spatialUnitId);
        String name = (unit != null) ? unit.getName() : ("#" + spatialUnitId);
        return langBean.msg("dialog.label.title.action.spatialContext") + " " + name;
    }


}
