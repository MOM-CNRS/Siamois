package fr.siamois.ui.api.openapi.v1.resource.breadcrumb;

import io.swagger.v3.oas.annotations.media.Schema;

/** One step of a fil d'Ariane: a fiche the client can navigate to. */
@Schema(description = "Une étape du fil d'Ariane")
public record BreadcrumbResource(
        @Schema(description = "Type d'entité (clé du registre front)", example = "recordingUnit")
        String type,
        @Schema(description = "Identifiant de la fiche")
        String id,
        @Schema(description = "Libellé affiché (identifiant de la fiche)")
        String label
) {
}
