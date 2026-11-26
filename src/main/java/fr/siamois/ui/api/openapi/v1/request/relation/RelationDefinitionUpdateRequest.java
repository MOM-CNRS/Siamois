package fr.siamois.ui.api.openapi.v1.request.relation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "La nature d'une relation, modifiable tant qu'elle n'a aucun lien")
public record RelationDefinitionUpdateRequest(
        @Schema(allowableValues = {"simple", "complex"}) String kind,
        Boolean hierarchical,
        @Schema(description = "Champ qualificatif, ou null pour aucun") Long qualifierField) {
}
