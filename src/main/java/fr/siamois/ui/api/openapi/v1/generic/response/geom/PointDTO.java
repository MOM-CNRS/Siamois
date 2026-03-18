package fr.siamois.ui.api.openapi.v1.generic.response.geom;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GeoJSON Point")
public class PointDTO extends GeometryDTO {

    @Schema(
            description = "[longitude, latitude]",
            example = "[4.83, 45.76]"
    )
    public double[] coordinates;

    public PointDTO() {
        this.type = "Point";
    }
}