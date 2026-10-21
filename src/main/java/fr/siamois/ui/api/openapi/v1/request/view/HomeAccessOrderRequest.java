package fr.siamois.ui.api.openapi.v1.request.view;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Nouvel ordre des cartes d'accès aux données de l'accueil")
public record HomeAccessOrderRequest(
        @Schema(description = "Organisation de l'accueil", required = true)
        Long organizationId,
        @Schema(description = "Identifiants des cartes, dans l'ordre voulu", required = true)
        List<Long> ids
) {
}
