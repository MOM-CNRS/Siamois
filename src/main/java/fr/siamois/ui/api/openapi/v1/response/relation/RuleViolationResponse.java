package fr.siamois.ui.api.openapi.v1.response.relation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Une règle enfreinte : bloquante (l'écriture est refusée) ou avertissement")
public record RuleViolationResponse(
        @Schema(allowableValues = {"NO_CYCLE", "SINGLE_CHOICE", "EXCLUSION", "ACCEPTED_KIND"}) String rule,
        boolean blocking,
        String message,
        @Schema(description = "Les liens en cause : le chemin d'une boucle, ou les liens existants qu'une restriction casserait")
        List<Long> links) {
}
