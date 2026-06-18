package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

@Schema(description = "Référence vers une entité liée (concept, personne, unité, etc.)")
public record ResourceRef(
        @Schema(description = "Identifiant de l'entité référencée", example = "42")
        String resourceId,

        @Schema(description = "Type de l'entité : concepts | persons | action-units | spatial-units | action-codes | recording-units",
                example = "concept")
        String resourceType,

        @Schema(description = "Libellé affichable de l'entité référencée")
        @Nullable String label
) {
}
