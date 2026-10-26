package fr.siamois.ui.api.openapi.v1.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code GET /api/v1/identifier-suggestions} — commun à toutes les entités à identifiant généré. */
@Schema(description = "Suggestion d'identifiant pour une nouvelle entité")
public record IdentifierSuggestionResponse(
        @Schema(description = "Identifiant suggéré, sans consommation du compteur ; null quand le projet "
                + "ne configure pas de format (l'identifiant est alors attribué à la création)", nullable = true)
        String suggestedIdentifier
) {
}
