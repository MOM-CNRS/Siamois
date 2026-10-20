package fr.siamois.ui.api.openapi.v1.request.view;

import fr.siamois.dto.view.TableViewState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ajout d'un accès aux données sur l'accueil : crée la vue (table, type…) et sa carte")
public record HomeAccessRequest(
        @Schema(description = "Organisation de l'accueil", required = true)
        Long organizationId,
        @Schema(description = "Table (clé du registre front, ex. recordingUnit)", required = true)
        String resourceType,
        @Schema(description = "Titre de la carte, qui est celui de la vue", required = true)
        String title,
        @Schema(description = "Ce que dit la carte : le nom du type montré (ou de la table)")
        String label,
        @Schema(description = "État de la vue : filtre sur le type choisi", required = true)
        TableViewState state
) {
}
