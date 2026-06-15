package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

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
        @Nullable String valueBinding
) {
}
