package fr.siamois.ui.api.openapi.v1.resource.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One neighbour of a project in {@code GET /api/v1/projects/{id}/siblings} — enough to render the
 * "fiche précédente/suivante" header buttons (label for the tooltip, resourceUri for the bookmark
 * bridge) without a second round-trip once the arrow is clicked (the click still navigates via
 * {@code id}, same as every other entity link in this API).
 */
@Data
@NoArgsConstructor
public class ProjectSiblingResource {

    @Schema(description = "Identifiant unique du projet voisin")
    private String id;

    @Schema(description = "Libellé du projet voisin (fullIdentifier, ou name à défaut) — affiché dans l'info-bulle")
    private String label;

    @Schema(description = "URI de ressource du projet voisin, identique à ActionUnitPanel.entityRessourceUri() côté JSF")
    private String resourceUri;

    public ProjectSiblingResource(String id, String label, String resourceUri) {
        this.id = id;
        this.label = label;
        this.resourceUri = resourceUri;
    }
}
