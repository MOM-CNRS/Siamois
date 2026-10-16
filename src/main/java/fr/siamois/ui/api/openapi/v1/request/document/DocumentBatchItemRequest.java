package fr.siamois.ui.api.openapi.v1.request.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "Un document du lot avec métadonnées et cibles de rattachement")
public class DocumentBatchItemRequest {

    @Schema(description = "Clé client pour corréler la réponse", example = "row-1")
    private String clientKey;

    @Schema(description = "Titre du document", requiredMode = Schema.RequiredMode.REQUIRED, example = "Photo coupe A")
    private String title;

    @Schema(description = "Description libre")
    private String description;

    @Schema(description = "Concept SIAD.NATURE")
    private Long natureConceptId;

    @Schema(description = "Concept SIAD.SCALE")
    private Long scaleConceptId;

    @Schema(description = "Concept SIAD.FORMAT")
    private Long formatConceptId;

    @Schema(description = "Cibles de rattachement (au moins une)", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DocumentBatchTargetRequest> targets = new ArrayList<>();
}
