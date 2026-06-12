package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;

import java.util.List;

public record RecordingUnitRelationsData(
        List<StratigraphicRelationshipDTO> stratigraphicRelationships,
        List<RecordingUnitSummaryDTO> parents,
        List<RecordingUnitSummaryDTO> children
) {}
