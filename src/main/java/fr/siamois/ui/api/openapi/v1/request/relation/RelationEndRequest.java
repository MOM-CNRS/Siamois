package fr.siamois.ui.api.openapi.v1.request.relation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Une fiche, désignée par sa collection d'API et son identifiant")
public record RelationEndRequest(
        @Schema(description = "Collection d'API de la fiche", example = "recording-units",
                allowableValues = {"recording-units", "finds", "phases", "containers", "documents", "studies", "projects"})
        String resourceType,
        @Schema(description = "Identifiant numérique de la fiche", example = "12")
        Long resourceId) {
}
