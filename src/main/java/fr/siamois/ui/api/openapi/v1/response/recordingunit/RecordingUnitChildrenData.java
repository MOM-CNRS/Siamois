package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Liste des UE enfants directs (hiérarchie) pour une unité d'enregistrement parente.
 */
@Schema(description = "Unités d'enregistrement rattachées comme enfants directs")
public record RecordingUnitChildrenData(

        @Schema(description = "Résumés des UE filles (recording_unit_hierarchy, fk_parent_id → parent)")
        List<RecordingUnitSummaryDTO> children
) {
}
