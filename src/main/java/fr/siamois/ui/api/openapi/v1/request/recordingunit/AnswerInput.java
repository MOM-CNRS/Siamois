package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.util.List;

@Schema(description = "Valeur à appliquer à un champ. Utiliser 'value' pour les scalaires, 'values' pour SELECT_MANY.")
public record AnswerInput(
        @Schema(description = "Valeur scalaire (String, Integer, ISO-8601 date, id numérique pour les références). null = vider le champ.")
        @Nullable Object value,

        @Schema(description = "Valeurs pour SELECT_MANY. [] = vider. null = ne pas toucher.")
        @Nullable List<Object> values
) {
}
