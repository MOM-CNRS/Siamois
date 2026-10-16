package fr.siamois.ui.api.openapi.v1.response.spatialunit;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

/**
 * Résultat d'autocomplétion d'unité spatiale .
 */
@Schema(description = "Lieu / unité spatiale résumé pour autocomplétion")
public record PlaceAutocompleteItemApi(
        @Schema(description = "spatial_unit_id à utiliser dans le patch projet", type = "string", example = "42")
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String name,
        @Nullable String code,
        @Nullable
        @Schema(description = "Type du lieu résolu dans la langue demandés")
        ResolvedConceptResource concept
) {
}
