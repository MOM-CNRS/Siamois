package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Schema(description = "Création d'une unité d'enregistrement")
public class RecordingUnitCreateRequest {

    @Schema(
            description = "Clé du projet (unité d'action) : identifiant numérique (action_unit_id), "
                    + "full_identifier ou identifiant court dans une organisation accessible.",
            example = "INST-PROJ-2024",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String projectId;

    @Schema(description = "Identifiant du concept de type d'UE (concept_id)", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private String typeId;

    @Schema(description = "Valeurs par fieldId. Scalaires via 'value', multi-valeurs via 'values'.")
    private Map<String, AnswerInput> answers = new HashMap<>();

    public Map<String, Object> getFieldAnswers() {
        Map<String, Object> legacy = new HashMap<>();
        if (answers != null) {
            answers.forEach((k, v) -> legacy.put(k, v != null ? v.value() : null));
        }
        return legacy;
    }
}
