package fr.siamois.dto.api;

import fr.siamois.dto.entity.ActionUnitDTO;

/**
 * Action unit (API "project") with aggregate counts for list endpoints.
 */
public record AccessibleProjectForApi(
        ActionUnitDTO actionUnit,
        long recordingUnitCount,
        long childActionUnitCount
) {
}
