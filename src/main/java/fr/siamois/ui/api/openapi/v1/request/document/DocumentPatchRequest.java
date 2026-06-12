package fr.siamois.ui.api.openapi.v1.request.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Mise à jour des métadonnées d'un document")
public class DocumentPatchRequest {

    @Schema(description = "Titre du document")
    private String title;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Identifiant du concept nature (SIAD.NATURE)")
    private Long natureConceptId;

    @Schema(description = "Identifiant du concept échelle (SIAD.SCALE)")
    private Long scaleConceptId;

    @Schema(description = "Identifiant du concept format (SIAD.FORMAT)")
    private Long formatConceptId;
}
