package fr.siamois.ui.api.openapi.v1.resource.project;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * One Envers revision of a project (plan §4/§5) — the fiche header's revision history, not a
 * separate tab. Thin wrapper over {@link fr.siamois.domain.services.history.HistoryAuditService},
 * which is already entity-agnostic; nothing here is Project-specific beyond the controller route.
 */
@Schema(description = "Une révision du projet")
public record ProjectHistoryEntryResource(
        @Schema(description = "Numéro de révision Envers")
        long revisionNumber,
        @Schema(description = "Date de la révision")
        OffsetDateTime revisionDate,
        @Schema(description = "ADD, MOD ou DEL")
        String revisionType,
        ProjectHistoryAuthorResource author
) {
}
