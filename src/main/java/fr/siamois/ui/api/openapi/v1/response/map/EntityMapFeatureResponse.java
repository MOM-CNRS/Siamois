package fr.siamois.ui.api.openapi.v1.response.map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One element placed on a map, outside of any list: the fiche shown beside a map view.
 */
public record EntityMapFeatureResponse(
        @Schema(description = "null quand l'élément n'a pas de géométrie exploitable (absente, sans SRID ou SRID inconnu)",
                nullable = true)
        EntityMapResponse.Feature feature) {
}
