package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Recording units of a list, placed on a map: each feature carries its geometry as GeoJSON in WGS84
 * (EPSG:4326), reprojected server-side from whatever SRID it was stored in.
 */
public record RecordingUnitMapResponse(List<Feature> features, Meta meta) {

    public record Feature(
            String id,
            String fullIdentifier,
            ResolvedConceptResource type,
            ValidationStatus validated,
            @Schema(description = "GeoJSON, EPSG:4326") JsonNode geometry) {
    }

    @Schema(description = "total : unités du filtre ; shown : placées sur la carte ; "
            + "withoutGeometry : sans géométrie exploitable (absente, sans SRID ou SRID inconnu) ; "
            + "truncated : le filtre dépasse le plafond, seules les premières unités ont été examinées.")
    public record Meta(long total, int shown, int withoutGeometry, boolean truncated) {
    }
}
