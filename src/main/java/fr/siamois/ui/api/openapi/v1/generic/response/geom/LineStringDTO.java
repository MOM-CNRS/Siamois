package fr.siamois.ui.api.openapi.v1.generic.response.geom;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GeoJSON LineString")
public class LineStringDTO extends GeometryDTO {

    @Schema(example = "[[4.83,45.76],[4.84,45.77]]")
    public double[][] coordinates;

    public LineStringDTO() {
        this.type = "LineString";
    }
}