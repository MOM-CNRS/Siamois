package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Suggestion d'identifiant pour une nouvelle unité d'enregistrement")
public record IdentifierSuggestionResponse(
        @Schema(description = "Identifiant suggéré (full_identifier), sans consommation du compteur")
        String suggestedIdentifier
) {
}
