package fr.siamois.ui.api.openapi.v1.response.bulk;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A bulk action running in the background: its progress and, element by element, what it did so far.
 *
 * @param id        the action's id, to poll GET /api/v1/bulk/jobs/{id}
 * @param status    where it stands
 * @param total     how many elements it acts on
 * @param processed how many it has processed so far
 * @param succeeded the elements it changed
 * @param failed    the elements it could not change, with their reason
 * @param error     why it stopped, when FAILED
 */
@Schema(description = "Action en lot exécutée en arrière-plan : avancement et bilan élément par élément")
public record BulkJobResource(
        @Schema(description = "Identifiant de la tâche, à interroger sur GET /api/v1/bulk/jobs/{id}") String id,
        @Schema(description = "RUNNING, DONE ou FAILED") BulkJobStatus status,
        @Schema(description = "Nombre d'éléments à traiter") int total,
        @Schema(description = "Nombre d'éléments déjà traités") int processed,
        @Schema(description = "Identifiants des éléments traités avec succès") List<Long> succeeded,
        @Schema(description = "Éléments en échec") List<BulkFailureResource> failed,
        @Schema(description = "Motif de l'arrêt, si FAILED") String error) {
}
