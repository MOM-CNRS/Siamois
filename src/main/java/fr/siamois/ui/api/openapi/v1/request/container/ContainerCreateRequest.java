package fr.siamois.ui.api.openapi.v1.request.container;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Création d'un contenant dans un projet. Mirrors {@code PhaseCreateRequest}'s own minimal shape
 * (projectId + typeId) — {@code ContainerNewUnitForm} only requires a type; every other detail
 * (spatial unit, dimensions, weight) is filled in afterward, on the fiche.
 */
@Data
@Schema(description = "Création d'un contenant")
public class ContainerCreateRequest {

    @Schema(
            description = "Clé du projet (unité d'action) : identifiant numérique (action_unit_id), "
                    + "full_identifier ou identifiant court dans une organisation accessible.",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String projectId;

    @Schema(description = "Identifiant du concept de type de contenant (concept_id)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String typeId;

}
