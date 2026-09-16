package fr.siamois.ui.api.openapi.v1.request.project;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Mise à jour partielle d'un projet (champs absents ou {@code null} = inchangé).
 */
@Data
@Schema(description = "Champs modifiables sur la fiche projet : nom, catégorie, dates, "
        + "commune (mainLocationId) et localisation précise (spatialContextSpatialUnitIds). "
        + "Champs absents = inchangés.")
public class ProjectPatchRequest {

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
}
