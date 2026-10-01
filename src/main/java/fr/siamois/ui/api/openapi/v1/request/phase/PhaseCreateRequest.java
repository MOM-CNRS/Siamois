package fr.siamois.ui.api.openapi.v1.request.phase;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Création d'une phase chronologique dans un projet. Mirrors {@code RecordingUnitCreateRequest}'s
 * own minimal shape (projectId + typeId) — {@code PhaseNewUnitForm} only requires a type; the
 * optional title is accepted here too since it's the form's only other field, but every other
 * detail (description, order, bounds, periods, keywords) is filled in afterward, on the fiche.
 */
@Data
@Schema(description = "Création d'une phase")
public class PhaseCreateRequest {

    @Schema(
            description = "Clé du projet (unité d'action) : identifiant numérique (action_unit_id), "
                    + "full_identifier ou identifiant court dans une organisation accessible.",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String projectId;

    @Schema(description = "Identifiant du concept de type de phase (concept_id)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String typeId;

    @Schema(description = "Titre de la phase")
    private String title;

}
