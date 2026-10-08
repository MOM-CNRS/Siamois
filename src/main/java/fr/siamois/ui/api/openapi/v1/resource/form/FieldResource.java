package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;

@Schema(description = "Définition d'un champ de formulaire")
public record FieldResource(
        @Schema(description = "Identifiant du champ (custom_field_id)", example = "3")
        String id,

        @Schema(description = "Type de ressource", example = "fields", allowableValues = {"fields"})
        String resourceType,

        @Schema(description = "Libellé affichable, résolu selon Accept-Language")
        String label,

        @Schema(description = "Type de réponse (SELECT_ONE_FROM_FIELD_CODE, TEXT, DATE, INTEGER, ...)")
        String answerType,

        @Schema(description = "Texte d'aide, résolu selon Accept-Language")
        @Nullable String hint,

        @Schema(description = "Vrai si le champ est un champ système (binding direct sur l'entité)")
        Boolean isSystemField,

        @Schema(description = "Nom de la propriété métier bindée si champ système (ex. openingDate)")
        @Nullable String valueBinding,

        @Schema(description = "Code de vocabulaire (ex. SIARU.CHRONO) pour les SELECT_*_FROM_FIELD_CODE")
        @Nullable String fieldCode,

        @Schema(description = "Vrai si un champ TEXT doit s'afficher sur plusieurs lignes "
                + "(CustomFieldText.isTextArea) ; null pour les autres types de champ")
        @Nullable Boolean isTextArea,

        @Schema(description = "Classe d'icône affichée devant le libellé du champ dans la fiche "
                + "(CustomField.getIcon)", example = "bi bi-question")
        @Nullable String icon,

        @Schema(description = "URI du concept du champ, cible du lien « Documentation » de la fiche "
                + "(CustomField.getConceptUri)")
        @Nullable String conceptUri,

        @Schema(description = "Contraintes de saisie du champ (bornes, heure, unité) ; null si aucune")
        @Nullable Constraints constraints,

        @Schema(description = "Tri et filtre de la colonne de liste du champ (clé = id du champ) ; "
                + "null si la colonne n'est ni triable ni filtrable")
        @Nullable Query query,

        @Schema(description = "Vrai si le champ n'est jamais modifiable, ni dans la fiche ni dans une liste "
                + "(colonne readOnly du formulaire de détail, ex. le projet d'une UE) ; null = modifiable")
        @Nullable Boolean readOnly,

        @Schema(description = "Règles conditionnelles du champ dans le formulaire de détail de son entité "
                + "(enabledWhen, requiredWhen, options, constraints — même langage que les colonnes du layout). "
                + "Là où une liste n'a pas de layout, c'est ce qui permet de griser une cellule ou d'en borner "
                + "la valeur ; null si le champ n'a aucune règle")
        @Nullable Map<String, Object> rules,

        @Schema(description = "Sources externes de suggestions d'un champ de lieu (INSEE : communes, GEOPLAT : adresses), "
                + "en plus des lieux de l'organisation ; null si le champ n'en a pas")
        @Nullable List<String> placeSources
) {

    /** The pre-rules shape. */
    public FieldResource(String id, String resourceType, String label, String answerType, String hint,
                         Boolean isSystemField, String valueBinding, String fieldCode, Boolean isTextArea,
                         String icon, String conceptUri, Constraints constraints, Query query, Boolean readOnly) {
        this(id, resourceType, label, answerType, hint, isSystemField, valueBinding, fieldCode, isTextArea,
                icon, conceptUri, constraints, query, readOnly, null, null);
    }

    /** The pre-read-only shape. */
    public FieldResource(String id, String resourceType, String label, String answerType, String hint,
                         Boolean isSystemField, String valueBinding, String fieldCode, Boolean isTextArea,
                         String icon, String conceptUri, Constraints constraints, Query query) {
        this(id, resourceType, label, answerType, hint, isSystemField, valueBinding, fieldCode, isTextArea,
                icon, conceptUri, constraints, query, null, null, null);
    }

    /** The pre-constraints shape, for callers that have none to give. */
    public FieldResource(String id, String resourceType, String label, String answerType, String hint,
                         Boolean isSystemField, String valueBinding, String fieldCode, Boolean isTextArea,
                         String icon, String conceptUri) {
        this(id, resourceType, label, answerType, hint, isSystemField, valueBinding, fieldCode, isTextArea,
                icon, conceptUri, null, null, null, null, null);
    }

    /** The pre-query shape. */
    public FieldResource(String id, String resourceType, String label, String answerType, String hint,
                         Boolean isSystemField, String valueBinding, String fieldCode, Boolean isTextArea,
                         String icon, String conceptUri, Constraints constraints) {
        this(id, resourceType, label, answerType, hint, isSystemField, valueBinding, fieldCode, isTextArea,
                icon, conceptUri, constraints, null, null, null, null);
    }

    public FieldResource withQuery(@Nullable Query query) {
        return new FieldResource(id, resourceType, label, answerType, hint, isSystemField, valueBinding, fieldCode,
                isTextArea, icon, conceptUri, constraints, query, readOnly, rules, placeSources);
    }

    public FieldResource withReadOnly(@Nullable Boolean readOnly) {
        return new FieldResource(id, resourceType, label, answerType, hint, isSystemField, valueBinding, fieldCode,
                isTextArea, icon, conceptUri, constraints, query, readOnly, rules, placeSources);
    }

    public FieldResource withPlaceSources(@Nullable List<String> placeSources) {
        return new FieldResource(id, resourceType, label, answerType, hint, isSystemField, valueBinding, fieldCode,
                isTextArea, icon, conceptUri, constraints, query, readOnly, rules, placeSources);
    }

    public FieldResource withRules(@Nullable Map<String, Object> rules) {
        return new FieldResource(id, resourceType, label, answerType, hint, isSystemField, valueBinding, fieldCode,
                isTextArea, icon, conceptUri, constraints, query, readOnly, rules, placeSources);
    }

    @Schema(description = "Contraintes de saisie d'un champ")
    public record Constraints(
            @Schema(description = "Borne basse (INTEGER, DECIMAL, MEASUREMENT)") @Nullable Double min,
            @Schema(description = "Borne haute (INTEGER, DECIMAL, MEASUREMENT)") @Nullable Double max,
            @Schema(description = "DATETIME : saisir aussi l'heure") @Nullable Boolean showTime,
            @Schema(description = "MEASUREMENT : symbole de l'unité du champ", example = "cm") @Nullable String unit
    ) {
    }

    @Schema(description = "Ce qu'une liste accepte sur la colonne du champ : sort=<id>:asc|desc, f.<id>…")
    public record Query(
            @Schema(description = "Triable (sort=<id>:asc) ; une colonne multivaluée se trie par nombre de valeurs")
            boolean sortable,
            @Schema(description = "Filtre accepté : contains (f.<id>=texte), range (f.<id>.from / f.<id>.to, "
                    + "nombres), date-range (idem, dates ISO), in (f.<id>=id répétable) ; null si aucun",
                    allowableValues = {"contains", "range", "date-range", "in"})
            @Nullable String filterOp
    ) {
    }
}
