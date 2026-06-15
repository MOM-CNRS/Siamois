package fr.siamois.ui.api.openapi.v1.resource.recordingunit.mobile;

import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Détail UE pour mobile : ressource + bundle formulaire + champs + vocabulaires par field_code.
 */
@Schema(description = "Détail UE avec formulaire et vocabulaires pour mode hors ligne")
public record RecordingUnitMobileDetailData(

        RecordingUnitResource recordingUnit,

        @Schema(description = "Formulaire effectif pour le type d'UE ; absent si aucune configuration")
        FormResource form,

        @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique)")
        Map<String, RecordingUnitFormFieldApi> fields,

        @Schema(description = "Listes de concepts possibles par field_code (champs SELECT_ONE_FROM_FIELD_CODE)")
        Map<String, List<ConceptAutocompleteDTO>> vocabulariesByFieldCode
) {
}
