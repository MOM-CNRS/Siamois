package fr.siamois.ui.bean.dialog.newunit;

import fr.siamois.domain.models.TraceableEntity;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

@Getter
@Builder
public class NewUnitContext implements Serializable {

    /** Type d’entité à créer */
    private final UnitKind kindToCreate;

    /** Contexte "scope" imposé par l’écran/table (ex: lié à une action X) */
    @Builder.Default
    private final Scope scope = Scope.none();

    /** D’où vient le clic (toolbar ou cellule) */
    @Builder.Default
    private final Trigger trigger = Trigger.toolbar();

    /** Politique d’insertion UI (liste + tree) */
    @Builder.Default
    private final UiInsertPolicy insertPolicy = UiInsertPolicy.defaultRoot();

    // --------- helpers factory ---------

    public static NewUnitContext toolbar(UnitKind kind) {
        return NewUnitContext.builder()
                .kindToCreate(kind)
                .trigger(Trigger.toolbar())
                .insertPolicy(UiInsertPolicy.defaultRoot())
                .scope(Scope.none())
                .build();
    }

    public static NewUnitContext cell(UnitKind kind, Long clickedId, String columnKey, UiInsertPolicy insertPolicy) {
        return NewUnitContext.builder()
                .kindToCreate(kind)
                .trigger(Trigger.cell(clickedId, columnKey))
                .insertPolicy(insertPolicy)
                .scope(Scope.none())
                .build();
    }

    // ======================
    // Scope
    // ======================
    @Getter
    @Builder
    public static class Scope implements Serializable {
        private final String key;          // ex: "ACTION", "SPATIAL", etc.
        private final Long entityId;       // ex: actionId
        private final String extra;        // optionnel

        public static Scope none() {
            return Scope.builder().key("NONE").build();
        }

        public static Scope linkedTo(String key, Long id) {
            return Scope.builder().key(key).entityId(id).build();
        }
    }

    // ======================
    // Trigger
    // ======================
    @Getter
    @Builder
    public static class Trigger implements Serializable {
        private final TriggerType type;
        private final Long clickedId;       // null si toolbar
        private final String columnKey;     // ex: "parents", "children", "action"

        public static Trigger toolbar() {
            return Trigger.builder().type(TriggerType.TOOLBAR).build();
        }

        public static Trigger cell(Long clickedId, String columnKey) {
            return Trigger.builder()
                    .type(TriggerType.CELL)
                    .clickedId(clickedId)
                    .columnKey(columnKey)
                    .build();
        }
    }

    public enum TriggerType { TOOLBAR, CELL }

    // ======================
    // UI insertion policy
    // ======================
    @Getter
    @Builder
    public static class UiInsertPolicy implements Serializable {

        @Builder.Default
        private final ListInsert listInsert = ListInsert.TOP;

        @Builder.Default
        private final TreeInsert treeInsert = TreeInsert.ROOT;

        public static UiInsertPolicy defaultRoot() {
            return UiInsertPolicy.builder().listInsert(ListInsert.TOP).treeInsert(TreeInsert.ROOT).build();
        }

        public static UiInsertPolicy childFirst(Long clickedId) {
            return UiInsertPolicy.builder().listInsert(ListInsert.TOP).treeInsert(TreeInsert.CHILD_FIRST).build();
        }

        public static UiInsertPolicy parentAtRoot(Long clickedId) {
            return UiInsertPolicy.builder().listInsert(ListInsert.TOP).treeInsert(TreeInsert.PARENT_AT_ROOT).build();
        }
    }

    public enum ListInsert { TOP }

    public enum TreeInsert {
        ROOT,              // bouton global -> racine
        CHILD_FIRST,       // ajouter un enfant en 1ère position sous clickedId
        PARENT_AT_ROOT     // ajouter un parent à la racine et re-parent le clickedId dessous
    }
}
