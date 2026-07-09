package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RecordingUnitResourceLinks {

    @Schema(description = "URL de l'UE")
    private String self;

    @Schema(description = "URL des mobiliers")
    private String recordingUnits;

    @Schema(description = "URL des enfants")
    private String children;

    @Schema(description = "URL des parents")
    private String parents;

    @Schema(description = "URL des documents")
    private String documents;

    public static RecordingUnitResourceLinks of(String projectId) {
        String base = "/recording-units/" + projectId;
        return new RecordingUnitResourceLinks(
                base,
                base + "/finds",
                base + "/children",
                base + "/parents",
                base + "/documents"
        );
    }
}
