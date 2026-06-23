package fr.siamois.ui.api.openapi.v1.resource.place;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceResourceCounts {

    @Schema(description = "Nombre de lieu enfants")
    private Long children;

    @Schema(description = "Nombre de projet")
    private Long projects;

    @Schema(description = "Nombre d'UE")
    private Long recordingUnits;

}
