package fr.siamois.ui.api.openapi.v1.generic.response.geom;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GeoJSON Polygon")
public class PolygonDTO extends GeometryDTO {

    @Schema(
            description = "",
            example = "[[[4.83,45.76],[4.84,45.76],[4.84,45.77],[4.83,45.76]]]"
    )
    public double[][][] coordinates;

    public PolygonDTO() {
        this.type = "Polygon";
    }
}