package fr.siamois.ui.api.openapi.v1.resource.view;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Vue par défaut d'un type, et droit de la modifier")
public record DefaultViewResponse(
        @Schema(description = "La vue par défaut, absente tant que personne n'en a enregistré") ViewResource view,
        @Schema(description = "L'utilisateur courant peut enregistrer la vue par défaut") boolean canEdit
) {
}
