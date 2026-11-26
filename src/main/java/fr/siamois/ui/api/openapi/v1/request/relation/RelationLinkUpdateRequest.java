package fr.siamois.ui.api.openapi.v1.request.relation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Le qualificatif et l'incertitude d'un lien ; ses deux fiches ne changent pas")
public record RelationLinkUpdateRequest(
        @Schema(description = "Côté du champ par lequel le lien est modifié", allowableValues = {"source", "target"},
                defaultValue = "source")
        String side,
        @Schema(description = "Qualificatif vu de ce côté (identifiant de concept), ou null pour l'effacer", example = "201")
        Long qualifier,
        @Schema(description = "Lien incertain", defaultValue = "false")
        Boolean uncertain) {
}
