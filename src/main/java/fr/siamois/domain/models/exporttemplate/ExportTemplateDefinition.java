package fr.siamois.domain.models.exporttemplate;

import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Partie partageable d'un modèle d'export : la structure du classeur à produire. Purement déclarative —
 * aucune expression libre, aucun SQL, aucun nom de table : les sources techniques et les navigations
 * sont des clés résolues côté code. Le format JSON est défini par {@link ExportTemplateJson}.
 *
 * <p>Les champs sont désignés par concept (thésaurus externe, voir {@link ConceptRef}), jamais par
 * identifiant de base, pour que le modèle circule d'une instance à l'autre.
 */
public record ExportTemplateDefinition(
        int schemaVersion,
        String id,
        String version,
        String name,
        @Nullable String fileNamePattern,
        List<Sheet> sheets) {

    /** Version courante de la grammaire JSON (mapping v3, partagée avec ShareQ3). */
    public static final int CURRENT_SCHEMA_VERSION = 3;

    /** Une feuille du classeur. {@code sortBy} liste des en-têtes de colonnes de la feuille. */
    public record Sheet(
            String name,
            boolean omitIfEmpty,
            List<Source> sources,
            List<String> sortBy,
            List<Column> columns) {
    }

    /** Colonne : un en-tête, un type de sortie, et des règles réparties sur les sources de la feuille. */
    public record Column(String header, OutputType output, List<Rule> rules) {
    }

    public enum OutputType { TEXT, NUMBER, DATE }

    public enum EntityKind { RECORDING_UNIT, SPECIMEN, PHASE, SPATIAL_UNIT, DOCUMENT, CONTAINER }

    /** Concept Opentheso : URI, identifiant externe du concept, identifiant externe du thésaurus. */
    public record ConceptRef(String thesaurusId, String conceptId, @Nullable String uri) {
    }

    // ------------------------------------------------------------------ sources

    public sealed interface Source permits EntitySource, ProjectSource, TechnicalSource, TableSource {
    }

    /** Entités d'un type donné du projet, filtrées par types (concepts) ; liste vide = tous les types. */
    public record EntitySource(EntityKind entity, List<ConceptRef> types) implements Source {
    }

    /** Le projet lui-même : une seule ligne. */
    public record ProjectSource() implements Source {
    }

    /** Table technique (hiérarchie, relation…), désignée par une clé enregistrée côté code. */
    public record TechnicalSource(String key) implements Source {
    }

    /**
     * Table nommée d'un schéma de fichier : source du cœur commun du langage (ShareQ3). Siamois sait la
     * lire et l'écrire sans l'exécuter : {@code ExportTemplateChecker} la signale comme non prise en charge.
     */
    public record TableSource(String name) implements Source {
    }

    // ------------------------------------------------------------------ champs

    public sealed interface FieldRef permits ConceptField, ColumnField, NameField {
    }

    /**
     * Champ désigné par son concept (champ système ou additionnel, résolu pour le projet).
     * {@code property} choisit une propriété imbriquée d'une mesure ({@code unit}, {@code comment}) ;
     * vide = la valeur du champ.
     */
    public record ConceptField(ConceptRef concept, @Nullable String property) implements FieldRef {

        /** Propriétés imbriquées d'une mesure, en plus de sa valeur. */
        public static final List<String> MEASUREMENT_PROPERTIES = List.of("unit", "comment");

        public ConceptField(ConceptRef concept) {
            this(concept, null);
        }
    }

    /** Colonne nommée d'une source technique (par exemple le type de relation). */
    public record ColumnField(String name) implements FieldRef {
    }

    /** Champ désigné par son nom dans un schéma (cœur commun, ShareQ3) ; non pris en charge par Siamois. */
    public record NameField(String name) implements FieldRef {
    }

    // ------------------------------------------------------------------ règles

    /**
     * Règle d'une colonne. {@code sources} liste les indices des sources de la feuille auxquelles elle
     * s'applique ; liste vide = toutes les sources.
     */
    public sealed interface Rule permits DirectRule, ConstantRule, ConcatRule {
        List<Integer> sources();
    }

    /**
     * Valeur d'un champ, éventuellement après navigation ({@code path} : suite de navigations à
     * cardinalité 1, par exemple {@code ["project"]}). {@code list} s'applique si le champ est multi-valué.
     */
    public record DirectRule(
            List<Integer> sources,
            FieldRef field,
            List<String> path,
            @Nullable ListOptions list) implements Rule {
    }

    public record ConstantRule(List<Integer> sources, String value) implements Rule {
    }

    /** Concaténation de parties (champ ou texte littéral), chacune éventuellement préfixée d'un libellé. */
    public record ConcatRule(
            List<Integer> sources,
            List<ConcatPart> parts,
            String separator,
            String labelSeparator) implements Rule {
    }

    /** Partie d'une concaténation : un champ (avec chemin) ou un littéral, jamais les deux. */
    public record ConcatPart(
            @Nullable String label,
            @Nullable String literal,
            @Nullable FieldRef field,
            List<String> path) {
    }

    /** Réduction d'un champ multi-valué en une cellule. */
    public record ListOptions(String separator, boolean sorted) {
    }
}
