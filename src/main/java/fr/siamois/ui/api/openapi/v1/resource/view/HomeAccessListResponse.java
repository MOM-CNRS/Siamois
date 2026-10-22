package fr.siamois.ui.api.openapi.v1.resource.view;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record HomeAccessListResponse(
        List<HomeAccessResource> data,
        @Schema(description = "L'utilisateur peut ajouter et retirer des accès") boolean canEdit
) {
}
