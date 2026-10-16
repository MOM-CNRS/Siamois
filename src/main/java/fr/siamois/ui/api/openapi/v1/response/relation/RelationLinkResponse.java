package fr.siamois.ui.api.openapi.v1.response.relation;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Un lien d'une relation configurable")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelationLinkResponse(
        @Schema(description = "Identifiant du lien", example = "7") Long id,
        @Schema(description = "Code de la relation", example = "SIAMOIS.RELATION.ORDER") String relation,
        @Schema(description = "Extrémité source") ResourceRef source,
        @Schema(description = "Extrémité cible") ResourceRef target,
        @Schema(description = "Type du lien vu du côté source (identifiant de concept)") Long sourceType,
        @Schema(description = "Type du lien vu du côté cible (identifiant de concept)") Long targetType,
        @Schema(description = "Attributs du lien, clés = identifiants de champ en chaîne") Map<String, Object> attributes,
        @Schema(description = "Règles non bloquantes que le lien enfreint") List<Warning> warnings
) {

    @Schema(description = "Une règle non bloquante enfreinte")
    public record Warning(
            @Schema(description = "Type de règle", example = "PAIR_CARDINALITY") String rule,
            @Schema(description = "Explication lisible") String message
    ) {
    }
}
