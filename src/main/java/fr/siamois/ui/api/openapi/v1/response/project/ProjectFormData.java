package fr.siamois.ui.api.openapi.v1.response.project;

import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormBundle;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormFieldApi;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Layout et champs du formulaire système projet (unité d'action).
 * Sur {@code GET /projects/form} : métadonnées UI seules ({@code currentValue} absent).
 * Sur {@code GET /projects/{id}/form} : inclut les valeurs persistées.
 * Vocabulaires : {@code GET /api/v1/vocabularies}.
 */
@Schema(description = "Formulaire projet (layout et champs)")
public record ProjectFormData(
        @Schema(description = "Bundle formulaire (layout JSON). formId est nul pour le formulaire système embarqué.")
        RecordingUnitFormBundle form,
        @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique)")
        Map<String, RecordingUnitFormFieldApi> fields
) {
}
