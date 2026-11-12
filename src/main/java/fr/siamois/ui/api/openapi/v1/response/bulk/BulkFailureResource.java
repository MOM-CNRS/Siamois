package fr.siamois.ui.api.openapi.v1.response.bulk;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un élément qu'une action en lot n'a pas pu traiter, et pourquoi.
 *
 * @param id      identifiant de l'élément
 * @param status  statut HTTP qu'aurait renvoyé l'appel unitaire (403, 404, 409…)
 * @param message message lisible
 */
@Schema(description = "Élément en échec d'une action en lot")
public record BulkFailureResource(
        @Schema(description = "Identifiant de l'élément", example = "13") long id,
        @Schema(description = "Statut HTTP de l'appel unitaire équivalent", example = "409") int status,
        @Schema(description = "Message lisible") String message) {
}
