package fr.siamois.ui.api.openapi.v1.resource.breadcrumb;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The ways up from a fiche to its project, for the React fil d'Ariane. {@code paths} is never empty;
 * its first element is the default path (the one shown), the others feed the "tous les chemins" overlay.
 * Every path runs from the project down to the fiche itself, both included.
 */
@Schema(description = "Chemins de la fiche jusqu'à son projet (le premier est le chemin par défaut)")
public record BreadcrumbsResource(
        @Schema(description = "Tous les chemins possibles, du projet à la fiche ; le premier est celui affiché par défaut")
        List<List<BreadcrumbResource>> paths,
        @Schema(description = "Vrai si la liste a été tronquée (graphe très connecté)")
        boolean truncated
) {
}
