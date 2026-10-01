package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import fr.siamois.domain.models.ValidationStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Schema(description = "Mise à jour partielle d'une unité d'enregistrement")
public class RecordingUnitPatchRequest {

    @Schema(description = "Nouveau statut de validation, absent = inchangé : INCOMPLETE (en cours), COMPLETE (terminé), "
            + "CANCELLED (annulé) avec le droit de modification ; VALIDATED (validé) — l'atteindre ou le quitter — "
            + "avec le droit validateur (_permissions.canValidate).")
    private ValidationStatus validated;

    @Schema(
            description = "Révision attendue (valeur de syncRevision au moment du chargement client). "
                    + "Si absente, aucun contrôle de conflit. "
                    + "Si présente et différente de la révision serveur → HTTP 409."
    )
    private Long expectedRevision;

    @Schema(description = "Valeurs par fieldId à fusionner. Clé absente = ne pas toucher. value:null = vider. values:[] = vider multi.")
    private Map<String, AnswerInput> answers = new HashMap<>();

    @Schema(description = "Géométrie de l'UE (GeoJSON), dans le SRID fourni ; aucune reprojection n'est effectuée. " +
            "Champ absent = inchangé ; null explicite = supprime la géométrie")
    private GeometryDTO geom;

    @JsonIgnore
    private boolean geomPresent;

    @JsonSetter("geom")
    public void setGeom(GeometryDTO geom) {
        this.geom = geom;
        this.geomPresent = true;
    }

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
