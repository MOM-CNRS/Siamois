package fr.siamois.ui.api.openapi.v1.response.spatialunit;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import fr.siamois.dto.entity.FullAddress;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

/**
 * Suggestion de lieu : un lieu de l'organisation (id renseigné) ou une suggestion d'une base externe (id null).
 */
@Schema(description = "Suggestion de lieu, interne (id renseigné) ou issue d'une source externe (id null)")
public record PlaceSuggestionItemApi(
        @Schema(description = "spatial_unit_id si le lieu existe déjà dans l'organisation ; null pour une suggestion externe", type = "string")
        @Nullable @JsonSerialize(using = ToStringSerializer.class) Long id,
        String name,
        @Nullable @Schema(description = "Code du lieu (code INSEE pour une commune)") String code,
        @Schema(description = "SIAMOIS, INSEE ou GEOPLAT", example = "INSEE") String source,
        @Nullable @Schema(description = "Type du lieu résolu dans la langue demandée") ResolvedConceptResource concept,
        @Nullable @Schema(description = "Adresse géolocalisée (suggestions GEOPLAT)") FullAddress address
) {
}
