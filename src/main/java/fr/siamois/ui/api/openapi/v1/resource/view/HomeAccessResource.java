package fr.siamois.ui.api.openapi.v1.resource.view;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Une carte d'accès aux données de l'accueil")
public record HomeAccessResource(
        Long id,
        @Schema(description = "La vue sur laquelle la carte ouvre la table") Long viewId,
        @Schema(description = "Table (clé du registre front)") String resourceType,
        @Schema(description = "Titre de la vue") String title,
        @Schema(description = "Ce que dit la carte : le nom du type montré (ou de la table)") String label,
        @Schema(description = "Filtres de la vue, pour compter ce qu'elle montre") Map<String, Object> filters
) {
}
