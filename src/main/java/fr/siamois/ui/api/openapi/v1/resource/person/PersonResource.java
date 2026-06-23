package fr.siamois.ui.api.openapi.v1.resource.person;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Utilisateur exposé par {@code GET /api/v1/users}.
 */
@Schema(description = "Utilisateur")
public record PersonResource(
        @Schema(description = "Identifiant personne (person_id)", example = "42")

        String id,
        @Schema(description = "Identifiant de connexion")
        String username,
        @Schema(description = "Prénom")
        String name,
        @Schema(description = "Nom de famille")
        String lastname
) {
}
