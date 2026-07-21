package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
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
    @JsonAlias("actionUnitId")
    private String projectId;

    @Schema(description = "Identifiant du concept de type d'UE (concept_id)", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonAlias("recordingUnitTypeConceptId")
    private String typeId;

    @Schema(description = "Valeurs par fieldId ({ value } / { values })")
    private Map<String, AnswerInput> answers = new HashMap<>();

    /**
     * Contrat legacy client mobile : scalaires / listes bruts indexés par fieldId.
     * Stockage interne uniquement — désérialisé via {@link #setFieldAnswers(Map)}.
     */
    @Schema(hidden = true)
    @JsonIgnore
    private Map<String, Object> legacyFieldAnswers;

    @JsonProperty("fieldAnswers")
    public void setFieldAnswers(Map<String, Object> fieldAnswers) {
        this.legacyFieldAnswers = fieldAnswers;
    }

    @JsonIgnore
    public Map<String, Object> getFieldAnswers() {
        return FieldAnswerMaps.merge(answers, legacyFieldAnswers);
    }
}
