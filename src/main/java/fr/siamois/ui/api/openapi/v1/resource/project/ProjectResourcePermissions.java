package fr.siamois.ui.api.openapi.v1.resource.project;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        boolean canDelete,
        // Projet seulement (PROJECT_MANAGE_SETTINGS, même règle que ActionUnitPanel.canOpenInProjectSettings) :
        // omis du JSON quand faux, donc absent des autres types d'entité qui partagent ce bloc.
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        @Schema(description = "Le caller peut ouvrir les paramètres de ce projet")
        boolean canManageSettings,
        // "Validateur" (PROJECT_VALIDATE and counterparts; ORGANIZATION_VALIDATE for a place): may set
        // the entity to VALIDATED or move it out of VALIDATED. Detail and list rows; omitted when false.
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        @Schema(description = "Le caller peut valider (ou dévalider) cette fiche")
        boolean canValidate
) {
    public static ProjectResourcePermissions of(boolean canWrite) {
        return new ProjectResourcePermissions(canWrite, canWrite, false, false);
    }

    public ProjectResourcePermissions withManageSettings(boolean canManageSettings) {
        return new ProjectResourcePermissions(canEdit, canDelete, canManageSettings, canValidate);
    }

    public ProjectResourcePermissions withValidate(boolean canValidate) {
        return new ProjectResourcePermissions(canEdit, canDelete, canManageSettings, canValidate);
    }
}
