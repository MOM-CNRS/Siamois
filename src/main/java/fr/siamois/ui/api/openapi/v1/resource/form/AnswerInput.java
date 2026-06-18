package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.util.List;

@Schema(description = "Valeur à appliquer à un champ. Utiliser 'value' pour les scalaires, 'values' pour SELECT_MANY.")
public record AnswerInput(
        @Schema(description = "Valeur scalaire ou ID (String, Integer, ISO-8601 date, id numérique pour les références). null = vider le champ.")
        @Nullable Object value,

        @Schema(description = "Valeurs ou ID pour SELECT_MANY. [] = vider. null = ne pas toucher.")
        @Nullable List<Object> values
) {
}
