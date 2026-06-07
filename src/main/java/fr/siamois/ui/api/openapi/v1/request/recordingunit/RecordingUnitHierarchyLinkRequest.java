package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Liaison hiérarchique vers une unité d'enregistrement déjà existante")
public class RecordingUnitHierarchyLinkRequest {

    @Schema(description = "Identifiant numérique recording_unit_id de l'UE à lier", example = "42")
    private Long relatedRecordingUnitId;
}
