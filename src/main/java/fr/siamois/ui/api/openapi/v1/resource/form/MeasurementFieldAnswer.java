package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

@Schema(description = "Champ de resourceType mesure (MEASUREMENT)")
public record MeasurementFieldAnswer(
        @Schema(description = "Discriminant — toujours MEASUREMENT", example = "MEASUREMENT")
        String answerType,

        @Schema(description = "Définition du champ")
        FieldResource field,

        @Schema(description = "Valeur de la mesure avec son unité")
        @Nullable MeasurementRef value
) implements FieldAnswer {
}
