package fr.siamois.ui.api.openapi.v1.request.view;

import fr.siamois.dto.view.TableViewState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Création ou modification d'une vue de liste")
public record ViewWriteRequest(
        @Schema(description = "Organisation de la vue", required = true)
        Long organizationId,
        @Schema(description = "Type d'entité (clé du registre front, ex. recordingUnit)", required = true)
        String resourceType,
        @Schema(description = "Titre", required = true)
        String title,
        @Schema(description = "Type de la fiche parente quand la vue liste les éléments liés à une fiche (ex. project)")
        String scopeEntityType,
        @Schema(description = "État de la liste", required = true)
        TableViewState state
) {
}
