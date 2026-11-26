package fr.siamois.ui.api.openapi.v1.request.relation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Les règles d'une relation dans une portée, entières : elles remplacent celles qui existaient")
public record RelationRulesRequest(
        @Schema(description = "Interdire les boucles (toujours vrai pour une relation hiérarchique)")
        Boolean noCycle,
        @Schema(description = "Côté source à choix unique : une fiche n'a qu'un lien de ce côté")
        Boolean sourceUnique,
        @Schema(description = "Côté cible à choix unique")
        Boolean targetUnique,
        @Schema(description = "Types acceptés de chaque côté ; aucun pour un côté : toute fiche")
        List<AcceptedKind> accepted,
        @Schema(description = "Paires qualificatif / miroir, un à un")
        List<MirrorPair> mirrors,
        @Schema(description = "Définitions exclusives de celle-ci : une même paire ne peut pas être reliée par les deux")
        List<Long> exclusions) {

    @Schema(description = "Un type de fiche accepté d'un côté")
    public record AcceptedKind(
            @Schema(allowableValues = {"source", "target"}) String side,
            @Schema(description = "Collection d'API", example = "recording-units") String resourceType,
            @Schema(description = "Sous-type exigé (identifiant du concept type), facultatif", example = "100") Long subtype) {
    }

    @Schema(description = "Un qualificatif et son miroir")
    public record MirrorPair(
            @Schema(description = "Concept du qualificatif", example = "201") Long concept,
            @Schema(description = "Concept miroir", example = "301") Long mirror) {
    }
}
