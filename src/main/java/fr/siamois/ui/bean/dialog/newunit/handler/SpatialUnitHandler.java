package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpatialUnitHandler implements INewUnitHandler<SpatialUnit> {

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
    public SpatialUnit newEmpty() {
        return new SpatialUnit();
    }

    @Override
    public SpatialUnit save(UserInfo u, SpatialUnit unit) throws EntityAlreadyExistsException {
        return spatialUnitService.save(u, unit);
    }

    @Override
    public String dialogWidgetVar() {
        return "newUnitDiag";
    }

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

            // We find the entity clicked
            SpatialUnit clicked = spatialUnitService.findById(clickedId);

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
        if (scope != null
                && "SPATIAL".equals(scope.getKey())
                && scope.getEntityId() != null) {

            SpatialUnit ref = spatialUnitService.findById(scope.getEntityId()); // adapte si Optional
            if (ref != null) {
                String extra = scope.getExtra();

                if ("PARENTS".equals(extra)) {
                    // La table représente "les parents de ref"
                    // => créer un nouveau parent : new.children += ref
                    unit.getChildren().add(ref);

                } else if ("CHILDREN".equals(extra)) {
                    // La table représente "les enfants de ref"
                    // => créer un nouvel enfant : new.parents += ref
                    unit.getParents().add(ref);

                } else {
                    // Scope spatial sans extra reconnu : no-op (ou logique par défaut)
                }
            }
        }
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

            SpatialUnit clicked = spatialUnitService.findById(trigger.getClickedId());
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

            SpatialUnit ref = spatialUnitService.findById(scope.getEntityId());
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
    public List<SpatialUnit> getSpatialUnitOptions(SpatialUnit unit) {
        return List.of();
    }

    @Override
    public String getName(SpatialUnit unit) {
        return unit.getName();
    }


}
