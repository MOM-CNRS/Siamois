package fr.siamois.ui.api.openapi.v1.request.bookmark;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Generic across any bookmarkable resource, not just Project (plan §5) — {@code resourceUri} is
 * whatever the resource's own plain-navigation URL is (Project: {@code /action-unit/{id}}, see
 * {@link fr.siamois.ui.api.openapi.v1.service.ProjectApiService#actionUnitResourceUri}), never the
 * REST API path.
 */
@Schema(description = "Création d'un favori")
public record BookmarkCreateRequest(
        @Schema(description = "URI de navigation de la ressource (ex. /action-unit/123), pas le chemin REST", required = true)
        String resourceUri,
        @Schema(description = "Code de titre (libellé générique i18n) ou titre de la ressource")
        String titleCode,
        @Schema(description = "Organisation dans laquelle le favori est enregistré (un favori est scopé par institution)", required = true)
        Long organizationId
) {
}
