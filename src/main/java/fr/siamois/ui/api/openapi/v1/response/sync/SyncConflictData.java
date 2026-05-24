package fr.siamois.ui.api.openapi.v1.response.sync;

import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitMobileDetailData;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corps renvoyé en HTTP 409 lors d'un conflit de révision.
 */
@Schema(description = "Conflit de synchronisation (version serveur plus récente)")
public record SyncConflictData(

        @Schema(description = "Type d'entité", example = "recording_unit")
        String entityType,

        @Schema(description = "Identifiant de l'entité côté serveur")
        String entityId,

        @Schema(description = "Révision que le client avait au moment de sa modification")
        Long expectedRevision,

        @Schema(description = "Révision actuelle sur le serveur")
        Long currentRevision,

        @Schema(description = "État serveur actuel (détail UE si entityType=recording_unit)")
        RecordingUnitMobileDetailData serverState
) {
}
