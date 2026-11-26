package fr.siamois.ui.api.openapi.v1.response.relation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "L'identité d'une relation, globale")
public record RelationDefinitionResponse(
        Long id,
        @Schema(description = "Champ du côté source") Long sourceField,
        @Schema(description = "Champ du côté cible, le même pour une relation symétrique") Long targetField,
        @Schema(allowableValues = {"simple", "complex"}) String kind,
        boolean symmetric,
        boolean hierarchical,
        @Schema(description = "Champ qualificatif d'une relation complexe") Long qualifierField,
        @Schema(description = "La relation a des liens : sa nature ne peut plus changer") boolean hasLinks) {
}
