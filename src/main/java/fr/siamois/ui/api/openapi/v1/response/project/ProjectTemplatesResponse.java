package fr.siamois.ui.api.openapi.v1.response.project;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Modèles de départ proposés à la création d'un projet")
public record ProjectTemplatesResponse(List<Template> templates) {

    @Schema(description = "Un modèle de départ")
    public record Template(
            @Schema(description = "Identifiant à renvoyer à la création : 'empty' ou 'project:<id>'") String id,
            @Schema(description = "Nom affiché (vide pour 'empty' : à libeller côté client)") String label,
            @Schema(description = "Nature du modèle", allowableValues = {"empty", "project"}) String kind) {
    }
}
