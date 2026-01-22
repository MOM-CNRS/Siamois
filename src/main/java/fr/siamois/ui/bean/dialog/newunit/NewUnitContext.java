package fr.siamois.ui.bean.dialog.newunit;

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

    private String updateOnCreate = "flow";

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
        private final UnitKind clickedKind;
        private final Long clickedId;
        private final String columnKey;

        public static Trigger toolbar() {
            return Trigger.builder().type(TriggerType.TOOLBAR).build();
        }

        public static Trigger cell(UnitKind clickedKind, Long clickedId, String columnKey) {
            return Trigger.builder()
                    .type(TriggerType.CELL)
                    .clickedKind(clickedKind)
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

    }

    public enum ListInsert { NONE, TOP }

    public enum TreeInsert {
        NONE, // pas d'insertion
        ROOT,              // bouton global -> racine
        CHILD_FIRST,       // ajouter un enfant en 1ère position sous clickedId
        PARENT_AT_ROOT     // ajouter un parent à la racine et re-parent le clickedId dessous
    }
}
