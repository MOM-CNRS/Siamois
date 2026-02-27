package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpatialUnitHandler implements INewUnitHandler<SpatialUnitDTO> {

    private final SpatialUnitService spatialUnitService;
    private final LangBean langBean;

    public SpatialUnitHandler(SpatialUnitService spatialUnitService, LangBean langBean
    ) {
        this.spatialUnitService = spatialUnitService;

        this.langBean = langBean;
    }

    @Override
    public UnitKind kind() {
        return UnitKind.SPATIAL;
    }

    @Override
    public SpatialUnitDTO newEmpty() {
        return new SpatialUnitDTO();
    }

    @Override
    public SpatialUnitDTO save(UserInfo u, SpatialUnitDTO unit) throws EntityAlreadyExistsException {
        return spatialUnitService.save(u, unit);
    }

    @Override
    public String dialogWidgetVar() {
        return "newUnitDiag";
    }

    @Override
    public void initFromContext(GenericNewUnitDialogBean<?> bean) {
        SpatialUnitDTO unit = (SpatialUnitDTO) bean.getUnit();
        NewUnitContext ctx = bean.getNewUnitContext();
        if (ctx == null) {
            return;
        }

        // Handle "cell" context: clicked node + column
        handleCellContext(ctx, unit);

        // Handle "table" scope: toolbar or row
        handleTableScope(ctx, unit);
    }

    private void handleCellContext(NewUnitContext ctx, SpatialUnitDTO unit) {
        NewUnitContext.Trigger trigger = ctx.getTrigger();
        if (trigger == null || trigger.getClickedId() == null || trigger.getColumnKey() == null) {
            return;
        }

        Long clickedId = trigger.getClickedId();
        String key = trigger.getColumnKey();
        SpatialUnitDTO clicked = spatialUnitService.findById(clickedId);

        if (clicked == null) {
            return;
        }

        switch (key) {
            case "parents" -> unit.getChildren().add(new SpatialUnitSummaryDTO(clicked));
            case "children" -> unit.getParents().add(new SpatialUnitSummaryDTO(clicked));
            default -> { /* no-op */ }
        }
    }

    private void handleTableScope(NewUnitContext ctx, SpatialUnitDTO unit) {
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope == null || !"SPATIAL".equals(scope.getKey()) || scope.getEntityId() == null) {
            return;
        }

        SpatialUnitDTO ref = spatialUnitService.findById(scope.getEntityId());
        if (ref == null) {
            return;
        }

        String extra = scope.getExtra();
        if ("PARENTS".equals(extra)) {
            unit.getChildren().add(new SpatialUnitSummaryDTO(ref));
        } else if ("CHILDREN".equals(extra)) {
            unit.getParents().add(new SpatialUnitSummaryDTO(ref));
        }
        // else: no-op
    }


    @Override
    public String getTitle(NewUnitContext ctx) {
        if (ctx == null) {
            return INewUnitHandler.super.getTitle(ctx);
        }

        // =========================
        // 1) CELL trigger (clic sur colonne)
        // =========================
        NewUnitContext.Trigger trigger = ctx.getTrigger();
        if (trigger != null
                && trigger.getType() == NewUnitContext.TriggerType.CELL
                && trigger.getClickedKind() == UnitKind.SPATIAL
                && trigger.getClickedId() != null
                && trigger.getColumnKey() != null) {

            SpatialUnitDTO clicked = spatialUnitService.findById(trigger.getClickedId());
            String name = clicked != null
                    ? clicked.getName()
                    : ("#" + trigger.getClickedId());

            return switch (trigger.getColumnKey()) {
                case "parents"  ->
                        langBean.msg("dialog.label.title.spatial.parent") + " " + name;
                case "children" ->
                        langBean.msg("dialog.label.title.spatial.child") + " " + name;
                default ->
                        INewUnitHandler.super.getTitle(ctx);
            };
        }

        // =========================
        // 2) TOOLBAR trigger (scope table)
        // =========================
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope != null
                && "SPATIAL".equals(scope.getKey())
                && scope.getEntityId() != null) {

            SpatialUnitDTO ref = spatialUnitService.findById(scope.getEntityId());
            String name = ref != null
                    ? ref.getName()
                    : ("#" + scope.getEntityId());

            if ("PARENTS".equals(scope.getExtra())) {
                return langBean.msg("dialog.label.title.spatial.parent") + " " + name;
            }
            if ("CHILDREN".equals(scope.getExtra())) {
                return langBean.msg("dialog.label.title.spatial.child") + " " + name;
            }
        }

        // =========================
        // 3) Default fallback
        // =========================
        return INewUnitHandler.super.getTitle(ctx);
    }


    @Override
    public List<SpatialUnitSummaryDTO> getSpatialUnitOptions(SpatialUnitDTO unit) {
        return List.of();
    }

    @Override
    public String getName(SpatialUnitDTO unit) {
        return unit.getName();
    }


}
