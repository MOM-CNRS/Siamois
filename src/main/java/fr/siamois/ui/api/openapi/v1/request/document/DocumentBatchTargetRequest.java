package fr.siamois.ui.api.openapi.v1.request.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cible de rattachement d'un document")
public class DocumentBatchTargetRequest {

    @Schema(
            description = "Type de ressource cible",
            allowableValues = {"projects", "recording-units", "finds", "places"},
            example = "recording-units",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String resourceType;

    @Schema(
            description = "Identifiant de la cible (id numérique ou full_identifier selon le type)",
            example = "187",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;
}
