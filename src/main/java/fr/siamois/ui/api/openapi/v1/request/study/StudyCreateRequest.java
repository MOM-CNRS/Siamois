package fr.siamois.ui.api.openapi.v1.request.study;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Création d'une étude dans un projet. Même forme minimale que {@code PhaseCreateRequest}
 * (projectId + typeId) ; tout le reste (liens aux UE et au mobilier, champs du traitement)
 * se renseigne ensuite sur la fiche.
 */
@Data
@Schema(description = "Création d'une étude")
public class StudyCreateRequest {

    @Schema(
            description = "Clé du projet (unité d'action) : identifiant numérique (action_unit_id), "
                    + "full_identifier ou identifiant court dans une organisation accessible.",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String projectId;

    @Schema(description = "Identifiant du concept de type d'étude (concept_id)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String typeId;

}
