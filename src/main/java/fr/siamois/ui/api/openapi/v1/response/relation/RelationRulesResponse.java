package fr.siamois.ui.api.openapi.v1.response.relation;

import fr.siamois.ui.api.openapi.v1.request.relation.RelationRulesRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Les règles d'une relation et la portée d'où elles viennent")
public record RelationRulesResponse(
        Long definition,
        @Schema(description = "Portée d'origine des règles", allowableValues = {"platform", "institution", "project", "none"})
        String scope,
        @Schema(description = "Les règles appartiennent à la portée demandée (sinon elles sont héritées)") boolean own,
        boolean noCycle,
        boolean sourceUnique,
        boolean targetUnique,
        List<RelationRulesRequest.AcceptedKind> accepted,
        List<RelationRulesRequest.MirrorPair> mirrors,
        List<Long> exclusions) {
}
