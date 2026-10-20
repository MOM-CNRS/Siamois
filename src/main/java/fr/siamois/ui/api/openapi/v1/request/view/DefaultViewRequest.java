package fr.siamois.ui.api.openapi.v1.request.view;

import fr.siamois.dto.view.TableViewState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Enregistrement de la vue par défaut d'un type")
public record DefaultViewRequest(
        @Schema(description = "Organisation", required = true)
        Long organizationId,
        @Schema(description = "Type d'entité (clé du registre front)", required = true)
        String resourceType,
        @Schema(description = "Projet (identifiant de l'unité d'action) ; absent pour une liste hors projet")
        Long projectId,
        @Schema(description = "Type de l'enregistrement ; absent pour tous les types")
        Long typeId,
        @Schema(description = "État de la liste", required = true)
        TableViewState state
) {
}
