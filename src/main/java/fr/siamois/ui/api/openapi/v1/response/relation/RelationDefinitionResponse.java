package fr.siamois.ui.api.openapi.v1.response.relation;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Une relation configurable : ce que chaque côté accepte et la correspondance de ses types")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelationDefinitionResponse(
        @Schema(description = "Code stable de la relation", example = "SIAMOIS.RELATION.ORDER") String code,
        @Schema(description = "Miroir : none, self (relation symétrique) ou other (autre champ)", example = "other") String mirror,
        @Schema(description = "Le côté miroir est-il modifiable") boolean mirrorEditable,
        @Schema(description = "Ce que le côté source accepte") List<Accepted> source,
        @Schema(description = "Ce que le côté cible accepte") List<Accepted> target,
        @Schema(description = "Correspondance entre le type du côté source et celui du côté cible") List<TypeMapping> typeMappings
) {

    @Schema(description = "Un genre d'entité accepté, éventuellement restreint à un type de configuration")
    public record Accepted(
            @Schema(description = "Collection de l'API", example = "recording-units") String resourceType,
            @Schema(description = "Type de configuration accepté (identifiant de concept) ; absent = tous les types") Long type
    ) {
    }

    @Schema(description = "Un couple de types qui se correspondent")
    public record TypeMapping(
            @Schema(description = "Type vu du côté source (identifiant de concept)") Long sourceType,
            @Schema(description = "Type vu du côté cible (identifiant de concept)") Long targetType,
            @Schema(description = "imposed : le serveur refuse tout autre couple ; suggested : simple préremplissage",
                    allowableValues = {"imposed", "suggested"}) String mode
    ) {
    }
}
