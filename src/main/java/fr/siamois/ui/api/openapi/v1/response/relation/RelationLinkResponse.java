package fr.siamois.ui.api.openapi.v1.response.relation;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Un lien, enregistré une fois de la source vers la cible")
public record RelationLinkResponse(
        Long id,
        @Schema(description = "Identifiant de la définition") Long definition,
        RelationEndResponse source,
        RelationEndResponse target,
        @Schema(description = "Qualificatif vu de la source (identifiant de concept)") Long qualifier,
        @Schema(description = "Qualificatif vu de la cible : son miroir dans les règles du projet") Long mirrorQualifier,
        boolean uncertain,
        @Schema(description = "Règles enfreintes sans refus : une boucle qui passe par un lien incertain")
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<RuleViolationResponse> warnings) {
}
