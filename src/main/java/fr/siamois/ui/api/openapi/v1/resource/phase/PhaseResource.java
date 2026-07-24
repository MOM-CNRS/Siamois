package fr.siamois.ui.api.openapi.v1.resource.phase;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Phase chronologique d'un projet")
public class PhaseResource {

    @Schema(description = "Type de ressource", example = "phases")
    private String resourceType = "phases";

    @Schema(description = "Identifiant technique (phase_id)")
    private String id;

    @Schema(description = "Identifiant métier de la phase")
    private String identifier;

    @Schema(description = "Titre de la phase")
    private String title;

    @Schema(description = "Libellé d'affichage (titre ou identifiant)")
    private String label;
}
