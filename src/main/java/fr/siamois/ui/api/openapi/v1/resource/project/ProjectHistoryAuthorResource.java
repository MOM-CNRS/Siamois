package fr.siamois.ui.api.openapi.v1.resource.project;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Auteur d'une révision")
public record ProjectHistoryAuthorResource(
        Long id,
        String name,
        String lastname
) {
}
