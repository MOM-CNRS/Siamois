package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Relation stratigraphique à créer entre l'UE de l'URL et une autre UE")
public class RecordingUnitStratigraphicRelationshipRequest {

    @Schema(description = "Identifiant numérique recording_unit_id de l'autre UE", example = "42")
    private Long relatedRecordingUnitId;

    @Schema(description = "Où se tient l'autre UE par rapport à l'UE de l'URL : anterior (plus ancienne), "
            + "posterior (plus récente) ou synchronous", allowableValues = {"anterior", "posterior", "synchronous"})
    private String position;

    @Schema(description = "Concept du type de relation (vocabulaire SIARU.STRATI), lu de l'UE de l'URL vers l'autre UE ; facultatif",
            example = "1234")
    private Long typeConceptId;

    @Schema(description = "Relation incertaine", defaultValue = "false")
    private Boolean uncertain;
}
