package fr.siamois.ui.api.openapi.v1.response.project;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How far a project creation followed by a progress token is.
 *
 * @param percentage the progress of the creation, between 0 and 100
 */
@Schema(description = "Avancement de la création d'un projet")
public record ProjectCreationProgressResponse(
        @Schema(description = "Avancement en pourcentage, de 0 (pas commencée ou inconnue) à 100 (terminée)",
                example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        int percentage) {
}
