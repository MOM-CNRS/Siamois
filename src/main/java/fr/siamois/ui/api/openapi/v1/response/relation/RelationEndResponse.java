package fr.siamois.ui.api.openapi.v1.response.relation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Une fiche à une extrémité d'un lien")
public record RelationEndResponse(
        @Schema(example = "recording-units") String resourceType,
        @Schema(example = "12") Long resourceId) {
}
