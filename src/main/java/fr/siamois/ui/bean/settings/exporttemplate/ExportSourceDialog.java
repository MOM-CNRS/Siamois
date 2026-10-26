package fr.siamois.ui.bean.settings.exporttemplate;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * État de la fenêtre « source de donnée » de l'éditeur : une table (le projet, une entité ou une table
 * technique), les types cochés, et les champs à importer vers des colonnes. Rien n'est écrit dans la
 * feuille avant la confirmation.
 */
@Getter
@Setter
public class ExportSourceDialog implements Serializable {

    public static final String TABLE_PROJECT = "PROJECT";
    public static final String TABLE_TECHNICAL_PREFIX = "TECH:";

    public static final String STATE_NONE = "NONE";
    public static final String STATE_SOME = "SOME";
    public static final String STATE_ALL = "ALL";

    /** Indice de la source modifiée dans la feuille ; -1 pour une nouvelle source. */
    private int editIndex = -1;
    /** Clé de la table choisie ({@link #TABLE_PROJECT}, un nom d'entité, ou {@code TECH:} + clé) ; nulle si rien n'est choisi. */
    private String table;
    /** Types cochés de la table choisie (clés {@code thesaurus|id|uri}). */
    private Set<String> types = new LinkedHashSet<>();
    private String expanded;
    private String query = "";
    private String fieldQuery = "";
    private List<FieldRow> fieldRows = new ArrayList<>();

    public void reset() {
        editIndex = -1;
        table = null;
        types = new LinkedHashSet<>();
        expanded = null;
        query = "";
        fieldQuery = "";
        fieldRows = new ArrayList<>();
    }

    public boolean isPicked() {
        return table != null;
    }

    /** Une table de l'arbre. {@code state} : aucun type coché, certains, ou tous (ou la table seule, si elle n'a pas de types). */
    @Getter
    public static class Table implements Serializable {
        private final String key;
        private final String label;
        private final String icon;
        private final String entityClass;
        private final String total;
        private final boolean expanded;
        private final String state;
        private final List<Type> types;

        public Table(String key, String label, ExportEntityStyle style, String total, boolean expanded, String state, List<Type> types) {
            this.key = key;
            this.label = label;
            this.icon = style.icon();
            this.entityClass = style.cssClass();
            this.total = total;
            this.expanded = expanded;
            this.state = state;
            this.types = types;
        }

        public boolean isHasTypes() {
            return !types.isEmpty();
        }

        public boolean isAll() {
            return STATE_ALL.equals(state);
        }

        public boolean isSome() {
            return STATE_SOME.equals(state);
        }
    }

    /** Un type d'une table, avec le nombre d'enregistrements du projet de référence (vide si le projet est inconnu). */
    @Getter
    public static class Type implements Serializable {
        private final String key;
        private final String label;
        private final String count;
        private final boolean empty;
        private final boolean picked;

        public Type(String key, String label, String count, boolean empty, boolean picked) {
            this.key = key;
            this.label = label;
            this.count = count;
            this.empty = empty;
            this.picked = picked;
        }
    }

    /** Un champ de la table, à importer vers une colonne ; décoché par défaut. */
    @Getter
    @Setter
    public static class FieldRow implements Serializable {
        private final String kind;
        private final String key;
        private final String label;
        private boolean checked;
        /** En-tête de la colonne cible ; vide = nouvelle colonne. */
        private String target = "";

        public FieldRow(String kind, String key, String label, String target) {
            this.kind = kind;
            this.key = key;
            this.label = label;
            this.target = target;
        }
    }

    /** Une source de la feuille, en puce : table, types, lignes du projet de référence et couverture des colonnes. */
    @Getter
    public static class Chip implements Serializable {
        private final transient ExportTemplateEditModel.EditSource source;
        private final int index;
        private final String table;
        private final String icon;
        private final String entityClass;
        private final String typesLabel;
        private final String rowsLabel;
        private final int covered;
        private final int total;
        private final boolean editable;
        private final boolean gapActive;

        public Chip(ExportTemplateEditModel.EditSource source, int index, String table, ExportEntityStyle style, String typesLabel,
                    String rowsLabel, int covered, int total, boolean editable, boolean gapActive) {
            this.source = source;
            this.index = index;
            this.table = table;
            this.icon = style.icon();
            this.entityClass = style.cssClass();
            this.typesLabel = typesLabel;
            this.rowsLabel = rowsLabel;
            this.covered = covered;
            this.total = total;
            this.editable = editable;
            this.gapActive = gapActive;
        }

        /** Vrai si toutes les colonnes de la feuille sont couvertes par cette source. */
        public boolean isFull() {
            return covered >= total;
        }

        /** Deuxième ligne de la puce : types · lignes. */
        public String getDetail() {
            return typesLabel.isEmpty() ? rowsLabel : rowsLabel.isEmpty() ? typesLabel : typesLabel + " · " + rowsLabel;
        }
    }
}
