package fr.siamois.ui.api.openapi.v1.resource.person;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Utilisateur exposé par {@code GET /api/v1/users} (ou créé par {@code POST /api/v1/projects/{id}/persons}).
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
        String lastname,
        @Schema(description = "Vrai pour une personne créée à partir de son seul nom, en attente de création de compte")
        boolean accountPending
) {
}
