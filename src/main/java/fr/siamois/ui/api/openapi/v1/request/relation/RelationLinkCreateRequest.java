package fr.siamois.ui.api.openapi.v1.request.relation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Un lien saisi depuis une fiche, par le champ d'un côté d'une relation")
public record RelationLinkCreateRequest(
        @Schema(description = "Champ relation de saisie (ex. -328) : donne la définition et le côté. À défaut, "
                + "definition et side", example = "-328")
        Long field,
        @Schema(description = "Identifiant de la définition de relation, si field est absent", example = "1")
        Long definition,
        @Schema(description = "Côté du champ de saisie : source (« Postérieur à ») ou target, son miroir (« Antérieur à »). "
                + "Sans effet pour une relation symétrique", allowableValues = {"source", "target"}, defaultValue = "source")
        String side,
        @Schema(description = "La fiche en cours de saisie")
        RelationEndRequest record,
        @Schema(description = "La fiche à relier")
        RelationEndRequest other,
        @Schema(description = "Relation complexe : le qualificatif tel que la fiche en cours de saisie le montre "
                + "(identifiant de concept). Saisi depuis le miroir, c'est le concept miroir : le lien enregistre son "
                + "correspondant", example = "201")
        Long qualifier,
        @Schema(description = "Lien incertain", defaultValue = "false")
        Boolean uncertain) {
}
