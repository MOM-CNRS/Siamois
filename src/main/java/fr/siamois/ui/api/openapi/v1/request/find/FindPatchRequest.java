package fr.siamois.ui.api.openapi.v1.request.find;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.FieldAnswerMaps;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Mise à jour partielle : seules les entrées présentes dans {@code fieldAnswers} sont fusionnées.
 */
@Data
@Schema(description = "Mise à jour partielle d'un mobilier (réponses formulaire)")
public class FindPatchRequest {

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
