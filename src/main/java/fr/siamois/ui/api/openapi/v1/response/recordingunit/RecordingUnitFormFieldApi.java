package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

/**
 * Définition et valeur courante d'un champ de formulaire pour l'app mobile (hors ligne).
 */
@Schema(description = "Champ de formulaire avec valeur courante")
public record RecordingUnitFormFieldApi(
        @Schema(description = "custom_field_id") Long fieldId,
        @Schema(description = "Type de réponse (discriminant JPA, ex. SELECT_ONE_FROM_FIELD_CODE)") String answerType,
        String label,

        String hint,

        @Schema(description = "Binding attribut métier si champ système") String valueBinding,
        Boolean isSystemField,

        @Schema(description = "Code thésaurus pour vocabulaire contrôlé (SELECT_ONE_FROM_FIELD_CODE)")
        @Nullable
        String fieldCode,

        @Schema(
                description = "Valeur saisie par l'utilisateur pour ce champ (issue du DTO / réponses formulaire persistées le cas échéant)",
                example = "\"texte\" ou objet structuré selon le type de champ")
        @JsonProperty("currentValue")
        @Nullable
        Object currentValue
) {
}
