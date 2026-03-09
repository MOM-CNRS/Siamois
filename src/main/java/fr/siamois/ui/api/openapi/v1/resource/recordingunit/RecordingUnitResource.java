package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import fr.siamois.ui.api.openapi.v1.jsonapi.ResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RecordingUnitResource implements ResourceIdentifier {

    @Schema(description = "Resource type (always 'recording-unit')",
            example = "recording-unit",
            allowableValues = {"recording-unit"})
    private String type;
    private String id;

}