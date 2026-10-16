package fr.siamois.ui.api.openapi.v1.request.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Une extrémité d'un lien : l'entité reliée")
public class RelationEndRequest {

    @Schema(description = "Collection d'API de l'entité", example = "recording-units",
            allowableValues = {"recording-units", "finds", "phases", "containers", "places", "projects", "persons",
                    "documents", "studies"})
    private String resourceType;

    @Schema(description = "Identifiant numérique de l'entité", example = "42")
    private Long resourceId;
}
