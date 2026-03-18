package fr.siamois.ui.api.openapi.v1.generic.response.geom;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "GeoJSON Geometry",
        discriminatorProperty = "type",
        oneOf = {
                PointDTO.class,
                MultiPointDTO.class,
                LineStringDTO.class,
                PolygonDTO.class,
                MultiPolygonDTO.class
        }
)
public abstract class GeometryDTO {

    @Schema(
            description = "GeoJSON type",
            example = "Point",
            allowableValues = {
                    "Point", "MultiPoint", "LineString",
                    "MultiLineString", "Polygon", "MultiPolygon"
            }
    )
    public String type;
}
