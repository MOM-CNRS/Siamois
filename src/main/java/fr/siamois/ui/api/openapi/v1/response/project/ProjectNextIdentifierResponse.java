package fr.siamois.ui.api.openapi.v1.response.project;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aperçu de l'identifiant du prochain projet de l'organisation")
public record ProjectNextIdentifierResponse(
        @Schema(description = "Prochain identifiant (non réservé), null si l'organisation ne peut pas numéroter ses projets")
        String identifier) {
}
