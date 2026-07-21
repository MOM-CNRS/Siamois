package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
