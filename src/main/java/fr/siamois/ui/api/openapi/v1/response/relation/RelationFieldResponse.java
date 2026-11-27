package fr.siamois.ui.api.openapi.v1.response.relation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Ce qu'un champ relation est dans un projet : de quoi le saisir")
public record RelationFieldResponse(
        Long field,
        RelationDefinitionResponse definition,
        @Schema(allowableValues = {"source", "target"}, description = "Le côté de ce champ (source pour une relation symétrique)")
        String side,
        @Schema(description = "Le champ de l'autre côté, son miroir") Long mirrorField,
        @Schema(description = "Une fiche n'a qu'un lien par ce champ") boolean singleChoice,
        @Schema(description = "Les fiches que ce champ peut relier (l'autre côté) ; aucune : toute fiche")
        List<AcceptedEnd> accepts,
        @Schema(description = "Relation complexe : les qualificatifs tels que ce champ les montre, avec le concept que le "
                + "lien enregistre. Vide quand la relation n'a pas de paire : le champ qualificatif propose alors ses "
                + "concepts tels quels")
        List<Qualifier> qualifiers,
        @Schema(description = "Le projet interdit les boucles") boolean noCycle) {

    public record AcceptedEnd(String resourceType, Long subtype) {
    }

    /** {@code shown}: the concept this field offers; {@code stored}: the one the link keeps. */
    public record Qualifier(Long shown, Long stored) {
    }
}
