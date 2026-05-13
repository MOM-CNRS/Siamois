package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Formulaire et vocabulaires pour la création d'une nouvelle UE (sans entité persistée).
 */
@Schema(description = "Formulaire de création d'unité d'enregistrement et listes de concepts par field_code")
public record RecordingUnitCreateFormData(

        @Schema(description = "Type d'UE (concept) demandé")
        ConceptDTO recordingUnitType,

        @Schema(description = "Formulaire effectif ; absent si aucune configuration pour ce type / institution")
        RecordingUnitFormBundle form,

        @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique)")
        Map<String, RecordingUnitFormFieldApi> fields,

        @Schema(description = "Listes de concepts possibles par field_code (champs SELECT_ONE_FROM_FIELD_CODE)")
        Map<String, List<ConceptAutocompleteDTO>> vocabulariesByFieldCode
) {
}
