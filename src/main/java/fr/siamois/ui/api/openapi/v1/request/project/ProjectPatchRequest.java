package fr.siamois.ui.api.openapi.v1.request.project;

import fr.siamois.domain.models.ValidationStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mise à jour partielle d'un projet (champs absents ou {@code null} = inchangé).
 */
@Data
@Schema(description = "Champs modifiables sur la fiche projet : nom, catégorie, dates, "
        + "commune (mainLocationId) et localisation précise (spatialContextSpatialUnitIds). "
        + "Champs absents = inchangés.")
public class ProjectPatchRequest {

    @Schema(description = "Nouveau statut de validation, absent = inchangé : INCOMPLETE (en cours), COMPLETE (terminé), "
            + "CANCELLED (annulé) avec le droit de modification ; VALIDATED (validé) — l'atteindre ou le quitter — "
            + "avec le droit validateur (_permissions.canValidate).")
    private ValidationStatus validated;

    @Schema(description = "Nom du projet")
    @JsonSetter(nulls = Nulls.FAIL)
    private String name;

    @Schema(description = "Identifiant court du projet dans l'organisation")
    @JsonSetter(nulls = Nulls.FAIL)
    private String identifier;

    @Schema(description = "Identifiant du concept du type de projet")
    @JsonSetter(nulls = Nulls.FAIL)
    @JsonAlias("typeConceptId")
    private String typeId;

    @Schema(description = "Date de début")
    private OffsetDateTime beginDate;

    @Schema(description = "Date de fin")
    private OffsetDateTime endDate;

    @Schema(description = "Localisation principale du projet / commune (identifiant d'unité spatiale)")
    private String mainLocationId;

    @Schema(description = "Localisations précises (identifiants d'unités spatiales)")
    private List<String> spatialContextSpatialUnitIds;

    @Schema(description = "Emprise du projet (GeoJSON), dans le SRID fourni ; aucune reprojection n'est effectuée. " +
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
     * Réponses aux champs de formulaire par id de champ, appliquées après les champs plats
     * ci-dessus (mêmes règles que {@code RecordingUnitPatchRequest.answers} : clé absente =
     * inchangé, {@code value:null} = vide, {@code values:null} = ne pas toucher, {@code values:[]}
     * = vide). Peut aussi cibler des champs déjà couverts par un champ plat (name, identifier,
     * beginDate, endDate, typeId, mainLocationId) — dans ce cas {@code answers} l'emporte, car
     * c'est le seul chemin que l'aperçu d'édition de liste connaît ; il n'a pas besoin de savoir
     * quels champs ont par ailleurs un alias plat.
     */
    @Schema(description = "Valeurs par fieldId à fusionner, appliquées après les champs plats ci-dessus. "
            + "Clé absente = ne pas toucher. value:null = vider. values:[] = vider (liste). "
            + "values:null = ne pas toucher (liste).")
    private Map<String, AnswerInput> answers = new HashMap<>();

    /** True when the patch changes nothing but the validation status (a validator's own edit). */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isStatusOnly() {
        return name == null && identifier == null && typeId == null && beginDate == null && endDate == null
                && mainLocationId == null && spatialContextSpatialUnitIds == null && !geomPresent
                && (answers == null || answers.isEmpty());
    }
}
