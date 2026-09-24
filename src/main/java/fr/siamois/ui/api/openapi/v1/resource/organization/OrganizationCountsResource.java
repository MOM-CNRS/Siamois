package fr.siamois.ui.api.openapi.v1.resource.organization;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Nombre d'entités de chaque type dans une organisation (cartes de l'accueil)")
public record OrganizationCountsResource(
        long projects,
        long places,
        long recordingUnits,
        long finds,
        long phases,
        long containers
) {
}
