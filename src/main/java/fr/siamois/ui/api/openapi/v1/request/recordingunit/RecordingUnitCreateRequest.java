package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
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

    @Schema(description = "Géométrie de l'UE (GeoJSON), dans le SRID fourni ; aucune reprojection n'est effectuée")
    private GeometryDTO geom;

    @Schema(description = "UE existante (recording_unit_id) dont la nouvelle UE devient l'enfant direct ; même projet obligatoire")
    private Long parentRecordingUnitId;

    @Schema(description = "UE existante (recording_unit_id) dont la nouvelle UE devient le parent direct ; même projet obligatoire")
    private Long childRecordingUnitId;

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
