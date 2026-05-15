package fr.siamois.ui.api.openapi.v1.response.find;

import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormBundle;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormFieldApi;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Formulaire d'un mobilier existant : layout et champs avec valeurs actuelles (sans vocabulaires).
 * Les listes de concepts sont disponibles via {@code GET /api/v1/vocabularies}.
 */
@Schema(description = "Formulaire d'édition d'un mobilier (layout et champs)")
public record FindMobilierFormData(

        @Schema(description = "Formulaire effectif ; absent si aucune configuration pour ce type / institution")
        RecordingUnitFormBundle form,

        @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique), avec currentValue")
        Map<String, RecordingUnitFormFieldApi> fields
) {
}
