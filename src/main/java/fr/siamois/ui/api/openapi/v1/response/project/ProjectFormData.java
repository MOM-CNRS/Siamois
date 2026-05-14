package fr.siamois.ui.api.openapi.v1.response.project;

import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormBundle;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormFieldApi;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Formulaire système de fiche projet (unité d'action) et vocabulaires des champs basés sur un field_code.
 */
@Schema(description = "Layout du formulaire projet, champs (métadonnées + valeurs courantes) et listes de concepts par field_code")
public record ProjectFormData(
        @Schema(description = "Bundle formulaire (layout JSON). formId est nul pour le formulaire système embarqué.")
        RecordingUnitFormBundle form,
        @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique), aligné sur GET recording-units/creation-form")
        Map<String, RecordingUnitFormFieldApi> fields,
        @Schema(description = "Listes de concepts possibles par field_code (ex. SIAAU.TYPE pour le type d'opération)")
        Map<String, List<ConceptAutocompleteDTO>> vocabulariesByFieldCode
) {
}
