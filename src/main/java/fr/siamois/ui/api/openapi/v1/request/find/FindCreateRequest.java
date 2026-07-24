package fr.siamois.ui.api.openapi.v1.request.find;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.FieldAnswerMaps;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Création d'un mobilier (spécimen) sur une UE : type (concept) et valeurs par id de champ
 * (clés = identifiants custom_field).
 */
@Data
@Schema(description = "Création d'un mobilier")
public class FindCreateRequest {

    @Schema(
            description = "Clé d'UE : identifiant numérique (recording_unit_id).",
            example = "187",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String recordingUnitId;

    @Schema(
            description = "Identifiant du concept de type de mobilier (concept_id, chaîne numérique). "
                    + "Alias JSON accepté : specimenTypeConceptId.",
            example = "42",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @JsonAlias("specimenTypeConceptId")
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
