package fr.siamois.ui.api.openapi.v1.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Mesure ajoutée à la volée à la fiche d'un enregistrement (« Ajouter une mesure »)")
public record OnTheFlyMeasurementRequest(
        @Schema(description = "Nom du panneau de la fiche où le champ est ajouté") String panel,
        @Schema(description = "Concept de ce qui est mesuré (SIAMD.TYPE) ; sert aussi de libellé") Long typeConceptId,
        @Schema(description = "Concept de la nature de la mesure (SIAMD.NATURE), facultatif") Long natureConceptId,
        @Schema(description = "Unité par défaut de la mesure") Long defaultUnitId,
        @Schema(description = "Autres unités proposées à la saisie, de même dimension que l'unité par défaut")
        List<Long> allowedUnitIds,
        @Schema(description = "Nom d'un nouveau type de mesure, à défaut de typeConceptId : un concept local, sans URI")
        String newTypeLabel,
        @Schema(description = "Nom d'une nouvelle nature de mesure, à défaut de natureConceptId : un concept local, sans URI")
        String newNatureLabel,
        @Schema(description = "Mesure existante de la même table à reprendre telle quelle (type, nature et unités) ; "
                + "les autres champs sont alors ignorés")
        Long copyFieldId) {
}
