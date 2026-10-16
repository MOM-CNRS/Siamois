package fr.siamois.ui.api.openapi.v1.request.place;

import fr.siamois.dto.entity.FullAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Création (ou récupération) d'un lieu à partir d'une suggestion externe")
public class PlaceFromSuggestionRequest {

    @Schema(description = "Institution propriétaire (doit être dans le périmètre JWT).", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long organizationId;

    @Schema(description = "Projet en cours d'édition : son droit d'écriture autorise la création. "
            + "Absent : droit de créer des projets dans l'organisation (formulaire de création de projet).")
    private String projectId;

    @Schema(description = "Source de la suggestion : INSEE ou GEOPLAT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String source;

    @Schema(description = "Nom de la suggestion", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Code INSEE de la commune (source INSEE)")
    private String code;

    @Schema(description = "Adresse de la suggestion (source GEOPLAT)")
    private FullAddress address;
}
