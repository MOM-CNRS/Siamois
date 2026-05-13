package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecordingUnitRelationsData {

    private List<StratigraphicRelationshipDTO> stratigraphicRelationships;
    /** Unités parentes (table {@code recording_unit_hierarchy}). */
    private List<RecordingUnitSummaryDTO> parents;
    /** Unités filles (table {@code recording_unit_hierarchy}). */
    private List<RecordingUnitSummaryDTO> children;
}
