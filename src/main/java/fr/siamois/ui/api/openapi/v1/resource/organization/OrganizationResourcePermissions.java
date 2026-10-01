package fr.siamois.ui.api.openapi.v1.resource.organization;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Permission flags computed server-side for the caller on this organization. Mirrors
 * {@link fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions}'s pattern —
 * the client gates the "create project" action on this block instead of guessing client-side.
 */
@Schema(description = "Droits du caller sur cette organisation")
public record OrganizationResourcePermissions(
        @Schema(description = "Le caller peut créer un projet dans cette organisation")
        boolean canCreateProjects
) {
}
