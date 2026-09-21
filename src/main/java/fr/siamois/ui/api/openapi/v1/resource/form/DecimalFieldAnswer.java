package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

@Schema(description = "Champ de resourceType décimal (DECIMAL)")
public record DecimalFieldAnswer(
        @Schema(description = "Discriminant — toujours DECIMAL", example = "DECIMAL")
        String answerType,

        @Schema(description = "Définition du champ")
        FieldResource field,

        @Schema(description = "Valeur décimale saisie")
        @Nullable Double value
) implements FieldAnswer {
}
