package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Formulaire effectif pour le type d'UE (layout JSON aligné sur {@code custom_form.layout}).
 */
@Schema(description = "Formulaire personnalisé pour le type d'unité d'enregistrement")
public record RecordingUnitFormBundle(
        @Schema(description = "Identifiant du formulaire (custom_form.form_id)") Long formId,
        String name,
        String description,
        @Schema(description = "Layout JSON (sections / lignes / colonnes / fieldId), sérialisé comme en base")
        String layoutJson
) {
}
