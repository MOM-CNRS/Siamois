package fr.siamois.ui.api.openapi.v1.request.relation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Une relation créée depuis un champ relation, avec ses règles par défaut de la plateforme")
public record RelationDefinitionCreateRequest(
        @Schema(description = "Champ relation du côté source", example = "-329") Long sourceField,
        @Schema(description = "Champ relation existant du côté cible ; absent : il est créé avec targetFieldLabel. "
                + "Ignoré pour une relation symétrique", example = "-328") Long targetField,
        @Schema(description = "Libellé du champ à créer du côté cible", example = "Antérieur à") String targetFieldLabel,
        @Schema(allowableValues = {"simple", "complex"}) String kind,
        @Schema(description = "La relation est son propre miroir (un seul champ)") Boolean symmetric,
        @Schema(description = "La relation range les fiches en arbre") Boolean hierarchical,
        @Schema(description = "Relation complexe : champ de vocabulaire à choix unique qui qualifie un lien") Long qualifierField,
        @Schema(description = "Règles par défaut de la plateforme") RelationRulesRequest defaultRules) {
}
