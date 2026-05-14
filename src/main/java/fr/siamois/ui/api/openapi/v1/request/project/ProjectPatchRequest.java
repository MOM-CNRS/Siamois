package fr.siamois.ui.api.openapi.v1.request.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Mise à jour partielle d'un projet (champs absents ou {@code null} = inchangé).
 * Seuls le nom, la catégorie (type d'opération), les dates et la localisation précise sont exposés à la modification.
 */
@Data
@Schema(description = "Champs modifiables sur la fiche projet : nom, catégorie, dates, localisation précise (contexte spatial). "
        + "Champs absents ou null = inchangés ; spatialContextSpatialUnitIds = [] retire tous les lieux de contexte.")
public class ProjectPatchRequest {

    @Schema(description = "Nom du projet")
    private String name;

    @Schema(description = "Date de début")
    private OffsetDateTime beginDate;

    @Schema(description = "Date de fin")
    private OffsetDateTime endDate;

    @Schema(description = "Identifiant du concept de catégorie / type d'opération (vocabulaire SIAAU.TYPE)")
    private Long typeConceptId;

    @Schema(description = "Identifiants des unités spatiales (spatial_unit_id) pour la localisation précise. "
            + "Absent ou null = inchangé ; liste vide = supprimer tout le contexte spatial.")
    private List<Long> spatialContextSpatialUnitIds;
}
