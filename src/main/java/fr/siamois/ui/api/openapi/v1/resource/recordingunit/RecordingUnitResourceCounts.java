package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecordingUnitResourceCounts {

    @Schema(description = "Nombre d'UE incluse")
    private Long children;

    @Schema(description = "Nombre de mobilier")
    private Long finds;

    @Schema(description = "Nombre parents")
    private Long parents;

    @Schema(description = "Nombre documents")
    private Long documents;

}
