package fr.siamois.ui.api.openapi.v1.request.view;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "État d'une vue pour l'utilisateur courant")
public record ViewStateRequest(
        @Schema(description = "Organisation", required = true)
        Long organizationId,
        @Schema(description = "Clé de l'état : view:<id>, ctx:<type>:<contexte> ou detail:<type>", required = true)
        String viewKey,
        @Schema(description = "État libre, interprété par le front", required = true)
        Map<String, Object> state
) {
}
