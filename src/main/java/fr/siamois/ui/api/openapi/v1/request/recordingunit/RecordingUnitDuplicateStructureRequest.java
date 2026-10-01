package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Duplication d'une UE et d'une partie de ses descendants")
public class RecordingUnitDuplicateStructureRequest {

    @Schema(description = "Nombre d'exemplaires de la structure (1 à 50, 1 par défaut)", example = "1")
    private Integer copies;

    @Schema(description = "Identifiants des descendants à inclure (l'UE elle-même l'est toujours). "
            + "Un descendant dont un ancêtre n'est pas dans la liste est ignoré, comme dans la modale JSF.")
    private List<Long> descendantIds;
}
