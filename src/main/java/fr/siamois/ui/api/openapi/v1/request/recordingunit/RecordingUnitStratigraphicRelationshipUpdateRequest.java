package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Nouvelles valeurs d'une relation stratigraphique existante (remplacement)")
public class RecordingUnitStratigraphicRelationshipUpdateRequest {

    @Schema(description = "Concept du type de relation, lu de l'UE de l'URL vers l'autre UE ; absent = aucun type")
    private Long typeConceptId;

    @Schema(description = "Relation incertaine", defaultValue = "false")
    private Boolean uncertain;
}
