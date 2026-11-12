package fr.siamois.ui.api.openapi.v1.request.person;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Création à la volée d'une personne, à partir de son seul nom.
 *
 * @param name     le prénom
 * @param lastname le nom de famille
 */
@Schema(description = "Création d'une personne en attente de création de compte")
public record PersonCreateRequest(
        @Schema(description = "Prénom", example = "Marie", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Nom de famille", example = "Curie", requiredMode = Schema.RequiredMode.REQUIRED)
        String lastname
) {
}
