package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

@Schema(description = "Champ de resourceType texte libre (TEXT)")
public record TextFieldAnswer(
        @Schema(description = "Discriminant — toujours TEXT", example = "TEXT")
        String answerType,

        @Schema(description = "Définition du champ")
        FieldResource field,

        @Schema(description = "Valeur textuelle saisie")
        @Nullable String value
) implements FieldAnswer {
}
