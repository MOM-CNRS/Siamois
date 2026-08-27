package fr.siamois.domain.services.recordingunit;

import fr.siamois.dto.entity.RecordingUnitDTO;

import java.util.List;

/**
 * Outcome of {@link RecordingUnitService#duplicateStructure(RecordingUnitDTO, java.util.Set, int)}.
 *
 * @param rootCopies  the copies of the duplicated entity itself (one per requested exemplar),
 *                    siblings of the original — this is what the UI inserts explicitly in tree mode.
 * @param allCreated  every entity created, at every level of the structure — used for the flat
 *                    list view (where units of any depth can be shown as rows) and for highlighting.
 */
public record RecordingUnitStructureDuplicationResult(
        List<RecordingUnitDTO> rootCopies,
        List<RecordingUnitDTO> allCreated
) {
}
