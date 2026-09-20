package fr.siamois.ui.api.openapi.v1.resource.project;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Permission flags computed server-side for the caller on this project, so the client (React
 * main-panel, plan §3) never has to guess or re-derive permissions — it just gates edit/delete
 * actions on this block. {@code canEdit}/{@code canDelete} share the same underlying check
 * ({@link fr.siamois.domain.services.permissions.ProfilePermissionService#hasActionUnitWritePermission}) —
 * no separate delete-permission concept exists anywhere in the model today, JSF included.
 */
@Schema(description = "Droits du caller sur ce projet")
public record ProjectResourcePermissions(
        @Schema(description = "Le caller peut modifier ce projet")
        boolean canEdit,
        @Schema(description = "Le caller peut supprimer ce projet")
        boolean canDelete
) {
    public static ProjectResourcePermissions of(boolean canWrite) {
        return new ProjectResourcePermissions(canWrite, canWrite);
    }
}
