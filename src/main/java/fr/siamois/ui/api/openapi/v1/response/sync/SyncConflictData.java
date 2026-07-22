package fr.siamois.ui.api.openapi.v1.response.sync;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Conflit de synchronisation (version serveur plus récente)")
public record SyncConflictData(

        @Schema(description = "Type d'entité", example = "recording-units")
        String resourceType,

        @Schema(description = "Identifiant de l'entité côté serveur")
        String resourceId,

        @Schema(description = "Révision que le client avait au moment de sa modification")
        Long expectedRevision,

        @Schema(description = "Révision actuelle sur le serveur")
        Long currentRevision,

        @Schema(description = "État serveur actuel de la ressource")
        Object serverState
) {
}
