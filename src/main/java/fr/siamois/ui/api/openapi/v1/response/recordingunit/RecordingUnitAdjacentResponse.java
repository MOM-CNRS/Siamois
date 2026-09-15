package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

/**
 * Réponse de {@code GET /api/v1/recording-units/{id}/adjacent} : l'unité précédente/suivante dans la
 * même unité d'action, par ordre de création (miroir de {@code RecordingUnitService.findNextByActionUnit}/
 * {@code findPreviousByActionUnit}, qui bouclent aux extrémités). Null si l'unité n'a pas d'unité d'action.
 */
public record RecordingUnitAdjacentResponse(
        @Schema(description = "Id de l'unité précédente (boucle sur la plus récente en fin de liste).")
        @Nullable Long previousId,
        @Schema(description = "Id de l'unité suivante (boucle sur la plus ancienne en fin de liste).")
        @Nullable Long nextId
) {
}
