package fr.siamois.ui.api.openapi.v1.resource.project;

import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.mobile.RecordingUnitFormFieldApi;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Layout et champs du formulaire système projet (unité d'action).
 * Métadonnées UI seules ({@code currentValue} absent) — voir {@code GET /api/v1/projects/form}.
 * Vocabulaires : {@code GET /api/v1/vocabularies}.
 */
@Schema(description = "Formulaire projet (layout et champs)")
public record ProjectFormData(
        @Schema(description = "Bundle formulaire (layout JSON). formId est nul pour le formulaire système embarqué.")
        FormResource form,
        @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique)")
        Map<String, RecordingUnitFormFieldApi> fields
) {
}
