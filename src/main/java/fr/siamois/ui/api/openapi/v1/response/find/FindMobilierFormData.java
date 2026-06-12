package fr.siamois.ui.api.openapi.v1.response.find;

import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormBundle;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormFieldApi;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Layout et champs d'un formulaire mobilier.
 * Sur {@code GET /mobiliers/form} : métadonnées UI seules ({@code currentValue} absent).
 * Sur {@code GET /mobiliers/{id}} : inclut les valeurs persistées.
 * Vocabulaires : {@code GET /api/v1/vocabularies}.
 */
@Schema(description = "Formulaire mobilier (layout et champs)")
public record FindMobilierFormData(

        @Schema(description = "Formulaire effectif ; absent si aucune configuration pour l'organisation")
        RecordingUnitFormBundle form,

        @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique)")
        Map<String, RecordingUnitFormFieldApi> fields
) {
}
