package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

@Schema(description = "Valeur de mesure avec son unité")
public record MeasurementRef(
        @Schema(description = "Valeur numérique saisie", example = "3.5")
        @Nullable Double numericValue,

        @Schema(description = "Symbole de l'unité", example = "cm")
        @Nullable String symbol,

        @Schema(description = "Valeur normalisée dans l'unité SI de référence")
        @Nullable Double normalizedValue,

        @Schema(description = "Commentaire associé à la mesure")
        @Nullable String comment
) {
}
