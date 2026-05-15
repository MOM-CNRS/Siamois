package fr.siamois.ui.api.openapi.v1.response.find;

import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormBundle;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormFieldApi;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Formulaire mobilier (création ou édition) : bundle, champs et vocabulaires (même modèle que le formulaire UE).
 */
@Schema(description = "Formulaire mobilier (layout, champs, vocabulaires)")
public record FindFormData(

        @Schema(description = "Type de mobilier (concept specimen_type)")
        ConceptDTO specimenType,

        @Schema(description = "Formulaire effectif ; absent si aucune configuration pour ce type / institution")
        RecordingUnitFormBundle form,

        @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique)")
        Map<String, RecordingUnitFormFieldApi> fields,

        @Schema(description = "Listes de concepts par field_code")
        Map<String, List<ConceptAutocompleteDTO>> vocabulariesByFieldCode
) {
}
