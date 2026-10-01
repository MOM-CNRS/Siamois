package fr.siamois.ui.api.openapi.v1.resource.sibling;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One neighbour in a {@code GET /api/v1/{collection}/{id}/siblings} response — same shape as
 * {@link fr.siamois.ui.api.openapi.v1.resource.project.ProjectSiblingResource}, for every other
 * entity type.
 */
public record SiblingResource(
        @Schema(description = "Identifiant de la fiche voisine")
        String id,
        @Schema(description = "Libellé de la fiche voisine (identifiant, ou nom) — affiché dans l'info-bulle")
        String label,
        @Schema(description = "URI de navigation/favori de la fiche voisine", example = "/recording-unit/42")
        String resourceUri
) {
}
