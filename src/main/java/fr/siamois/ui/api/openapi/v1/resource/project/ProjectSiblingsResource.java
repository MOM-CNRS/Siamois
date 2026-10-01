package fr.siamois.ui.api.openapi.v1.resource.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body of {@code GET /api/v1/projects/{id}/siblings} — the "fiche précédente/suivante"
 * pair, mirroring {@code AbstractSingleEntityPanel.goToPrevious/goToNext} (plan: prev/next
 * navigation on the React fiche panel). Either side is {@code null} when the caller's accessible
 * project set has no other project at all — deliberately not JSF's own wrap-to-self behaviour,
 * since a self-link is not a useful sibling (see {@code ActionUnitService.findSiblingProject}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSiblingsResource {

    @Schema(description = "Le projet précédent dans l'ordre effectif, ou null s'il n'y en a pas d'autre")
    private ProjectSiblingResource previous;

    @Schema(description = "Le projet suivant dans l'ordre effectif, ou null s'il n'y en a pas d'autre")
    private ProjectSiblingResource next;
}
