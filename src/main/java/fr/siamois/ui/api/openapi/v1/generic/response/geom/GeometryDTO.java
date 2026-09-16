package fr.siamois.ui.api.openapi.v1.generic.response.geom;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PointDTO.class, name = "Point"),
        @JsonSubTypes.Type(value = MultiPointDTO.class, name = "MultiPoint"),
        @JsonSubTypes.Type(value = LineStringDTO.class, name = "LineString"),
        @JsonSubTypes.Type(value = PolygonDTO.class, name = "Polygon"),
        @JsonSubTypes.Type(value = MultiPolygonDTO.class, name = "MultiPolygon")
})
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

    @Schema(
            description = "Code EPSG du système de coordonnées des données fournies. " +
                    "Aucune reprojection n'est effectuée : la géométrie est stockée telle quelle dans ce SRID.",
            example = "4326"
    )
    public Integer srid;
}
