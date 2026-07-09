package fr.siamois.ui.api.openapi.v1.resource.document;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Métadonnée d'un champ du formulaire document (équivalent fonctionnel au dialogue de création web).
 */
@Schema(description = "Définition d'un champ du formulaire création / édition de document")
public record DocumentFormFieldApi(

        @Schema(description = "Clé stable du champ", example = "nature")
        String fieldKey,

        @Schema(description = "Type de saisie : TEXT, TEXTAREA, CONCEPT_SELECT, FILE", example = "CONCEPT_SELECT")
        String inputType,

        @Schema(description = "Code métier du thésaurus (SIAD.*) pour les champs CONCEPT_SELECT", example = "SIAD.NATURE", nullable = true)
        String fieldCode,

        @Schema(description = "Longueur max pour TEXT / TEXTAREA quand applicable", nullable = true)
        Integer maxLength
) {
}
