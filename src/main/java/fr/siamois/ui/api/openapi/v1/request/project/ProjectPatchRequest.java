package fr.siamois.ui.api.openapi.v1.request.project;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Mise à jour partielle d'un projet (champs absents ou {@code null} = inchangé).
 */
@Data
@Schema(description = "Champs modifiables sur la fiche projet : nom, catégorie, dates, localisation précise (contexte spatial). "
        + "Champs absents = inchangés ;")
public class ProjectPatchRequest {

    @Schema(description = "Nom du projet")
    @JsonSetter(nulls = Nulls.FAIL)
    private String name;

    @Schema(description = "Identifiant court du projet dans l'organisation")
    @JsonSetter(nulls = Nulls.FAIL)
    private String identifier;

    @Schema(description = "Identifiant du concept du type de projet")
    @JsonSetter(nulls = Nulls.FAIL)
    private String typeId;

    @Schema(description = "Date de début")
    private OffsetDateTime beginDate;

    @Schema(description = "Date de fin")
    private OffsetDateTime endDate;

    @Schema(description = "Localisation principale du projet (Identifiant d'unité spatiale)")
    private String mainLocationId;
}
