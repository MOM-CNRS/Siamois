package fr.siamois.ui.api.openapi.v1.request.view;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Enregistrement de la configuration des onglets d'une fiche")
public record FicheConfigRequest(
        @Schema(description = "Organisation", required = true)
        Long organizationId,
        @Schema(description = "Type de fiche (clé du registre front)", required = true)
        String resourceType,
        @Schema(description = "Projet (identifiant de l'unité d'action) ; absent pour un type hors projet")
        Long projectId,
        @Schema(description = "Sous-type de la fiche ; absent pour tous les sous-types")
        Long typeId,
        @Schema(description = "Les onglets : {v, tabs:[{key, label?, hidden?, kind?, target?, baseFilters?}]}", required = true)
        Map<String, Object> tabs
) {
}
