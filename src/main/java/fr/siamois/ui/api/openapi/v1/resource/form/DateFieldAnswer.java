package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;

@Schema(description = "Champ de resourceType date/heure (DATETIME)")
public record DateFieldAnswer(
        @Schema(description = "Discriminant — toujours DATETIME", example = "DATETIME")
        String answerType,

        @Schema(description = "Définition du champ")
        FieldResource field,

        @Schema(description = "Valeur date-heure en UTC (ISO-8601 avec offset)", example = "2024-03-15T00:00:00Z")
        @Nullable OffsetDateTime value
) implements FieldAnswer {
}
