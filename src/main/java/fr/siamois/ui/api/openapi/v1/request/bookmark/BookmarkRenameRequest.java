package fr.siamois.ui.api.openapi.v1.request.bookmark;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Renommage d'un favori")
public record BookmarkRenameRequest(
        @Schema(description = "URI de navigation du favori, la même qu'à sa création", required = true)
        String resourceUri,
        @Schema(description = "Nouveau nom du favori", required = true)
        String title,
        @Schema(description = "Organisation du favori", required = true)
        Long organizationId
) {
}
