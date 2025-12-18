package fr.siamois.ui.bean.dialog.newunit.handler;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpatialUnitHandler implements INewUnitHandler<SpatialUnit> {

    private final SpatialUnitService spatialUnitService;

    public SpatialUnitHandler(SpatialUnitService spatialUnitService) {
        this.spatialUnitService = spatialUnitService;
    }

    @Override public UnitKind kind() { return UnitKind.SPATIAL; }
    @Override public SpatialUnit newEmpty() { return new SpatialUnit(); }
    @Override public SpatialUnit save(UserInfo u, SpatialUnit unit) throws EntityAlreadyExistsException { return spatialUnitService.save(u, unit); }
    @Override public String dialogWidgetVar() { return "newUnitDiag"; }

    @Override
    public void initFromContext(GenericNewUnitDialogBean<?> bean) {

        SpatialUnit unit = (SpatialUnit) bean.getUnit();
        NewUnitContext ctx = bean.getNewUnitContext();
        if (ctx == null) {
            return;
        }

        // 1) Contexte "cellule" : on sait sur quel node on a cliqué + quelle colonne
        NewUnitContext.Trigger trigger = ctx.getTrigger();
        if (trigger != null && trigger.getClickedId() != null && trigger.getColumnKey() != null) {

            Long clickedId = trigger.getClickedId();
            String key = trigger.getColumnKey();

            // ⚠️ il faut récupérer l'entité cliquée (on a juste son id)
            SpatialUnit clicked = spatialUnitService.findById(clickedId);
            // -> adapte selon ton service (Optional, getById, etc.)

            if (clicked != null) {
                switch (key) {
                    case "parents" -> {
                        // Tu crées un "parent" de clicked => relation : newUnit.children += clicked
                        unit.getChildren().add(clicked);
                    }
                    case "children" -> {
                        // Tu crées un "enfant" de clicked => relation : newUnit.parents += clicked
                        unit.getParents().add(clicked);
                    }
                    default -> {
                        // rien
                    }
                }
            }
        }

        // 2) Scope "table" : appliqué quoi qu'il arrive (toolbar ou ligne)
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope != null && scope.getEntityId() != null) {
            // exemple si un jour tu as : table "SpatialUnits liées à X"
            // switch (scope.getKey()) { ... }
        }
    }

    @Override
    public String getTitle(NewUnitContext ctx) {
        if (ctx == null || ctx.getTrigger() == null) {
            return INewUnitHandler.super.getTitle(ctx);
        }

        var trigger = ctx.getTrigger();
        Long clickedId = trigger.getClickedId();
        String columnKey = trigger.getColumnKey();

        if (clickedId == null || columnKey == null) {
            return INewUnitHandler.super.getTitle(ctx);
        }

        SpatialUnit clicked = spatialUnitService.findById(clickedId);
        String name = (clicked != null) ? clicked.getName() : ("#" + clickedId);

        return switch (columnKey) {
            case "parents"  -> "Nouvelle unité spatiale parente de " + name;
            case "children" -> "Nouvelle unité spatiale enfant de " + name;
            default         -> INewUnitHandler.super.getTitle(ctx);
        };
    }

    @Override
    public List<SpatialUnit> getSpatialUnitOptions(SpatialUnit unit) {
        return List.of();
    }

    @Override
    public String getName(SpatialUnit unit) {
        return unit.getName();
    }


}
