package fr.siamois.ui.api.openapi.v1.resource.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResourceCounts {

    @Schema(description = "Nombre de sous-projets")
    private Long children;

    @Schema(description = "Nombre d'unités d'enregistrement")
    private Long recordingUnits;

}
