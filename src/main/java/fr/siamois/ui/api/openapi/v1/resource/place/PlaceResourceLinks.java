package fr.siamois.ui.api.openapi.v1.resource.place;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlaceResourceLinks {

    @Schema(description = "URL de l'UE")
    private String self;

    @Schema(description = "URL des lieux enfants")
    private String children;

    @Schema(description = "URL des projet liés")
    private String projects;

    @Schema(description = "URL des UE")
    private String recordingUnits;

    public static PlaceResourceLinks of(String projectId) {
        String base = "/places/" + projectId;
        return new PlaceResourceLinks(
                base,
                base + "/children",
                base + "/projects",
                base + "/recording-units"
        );
    }
}
