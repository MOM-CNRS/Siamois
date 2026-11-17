package fr.siamois.ui.api.openapi.v1.response.map;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The rows of a list placed on a map, whatever the collection: each feature carries its geometry as
 * GeoJSON in WGS84 (EPSG:4326), reprojected server-side from whatever SRID it was stored in. What the
 * client derives (rows without geometry, truncation, colours) is left to it.
 */
public record EntityMapResponse(List<Feature> features, Meta meta) {

    public record Feature(
            String id,
            @Schema(description = "Libellé affiché de l'élément (ex. identifiant complet d'une UE)") String label,
            ResolvedConceptResource type,
            ValidationStatus validationStatus,
            @Schema(description = "GeoJSON, EPSG:4326") JsonNode geometry) {
    }

    @Schema(description = "total : éléments du filtre dont la géométrie affichée est renseignée ; examined : "
            + "éléments examinés (au plus le plafond) ; listTotal : éléments du filtre, géométrie renseignée ou non. "
            + "Ceux examinés mais absents de features n'ont pas de géométrie exploitable (illisible, sans SRID "
            + "ou SRID inconnu) ; listTotal - total n'ont pas de géométrie ; total > examined signifie que la "
            + "carte est tronquée.")
    public record Meta(long total, int examined, long listTotal) {
    }
}
