package fr.siamois.ui.api.openapi.v1.resource.project;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Défaut d'affichage d'une colonne de la liste des projets — visibilité et ordre, source unique de
 * vérité partagée entre la table JSF ({@code ActionUnitTableDefinitionFactory}) et la liste React,
 * pour qu'une constante TypeScript dupliquant cette liste ne puisse pas diverger silencieusement.
 *
 * <p>Ne couvre pas les colonnes structurelles (chip identifiant, nom, compteur d'unités
 * d'enregistrement) : elles ne sont pas togglables et ne viennent pas du catalogue de champs.</p>
 */
@Schema(description = "Défaut d'affichage d'une colonne de la liste des projets")
public record ProjectTableColumnResource(
        @Schema(description = "Identifiant de colonne (stable, historique JSF)", example = "status")
        String columnId,

        @Schema(description = "Identifiant du champ dans le catalogue fields", example = "-118")
        String fieldId,

        @Schema(description = "Visible par défaut (sinon disponible dans le sélecteur de colonnes)")
        boolean visible,

        @Schema(description = "Ordre d'affichage (0-based)")
        int order
) {
}
