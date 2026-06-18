package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Schema(description = "Mise à jour partielle d'une unité d'enregistrement")
public class RecordingUnitPatchRequest {

    @Schema(
            description = "Révision attendue (valeur de syncRevision au moment du chargement client). "
                    + "Si absente, aucun contrôle de conflit. "
                    + "Si présente et différente de la révision serveur → HTTP 409."
    )
    private Long expectedRevision;

    @Schema(description = "Valeurs par fieldId à fusionner. Clé absente = ne pas toucher. value:null = vider. values:[] = vider multi.")
    private Map<String, AnswerInput> answers = new HashMap<>();

    public Map<String, Object> getFieldAnswers() {
        Map<String, Object> legacy = new HashMap<>();
        if (answers != null) {
            answers.forEach((k, v) -> legacy.put(k, v != null ? v.value() : null));
        }
        return legacy;
    }
}
