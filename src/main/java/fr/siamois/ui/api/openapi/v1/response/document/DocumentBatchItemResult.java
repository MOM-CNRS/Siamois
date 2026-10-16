package fr.siamois.ui.api.openapi.v1.response.document;

import fr.siamois.ui.api.openapi.v1.request.document.DocumentBatchTargetRequest;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Résultat d'un item du lot documents")
public class DocumentBatchItemResult {

    @Schema(description = "Clé client renvoyée telle quelle")
    private String clientKey;

    @Schema(description = "created | failed", example = "created")
    private String status;

    @Schema(description = "Document créé (si status=created)")
    private DocumentResource document;

    @Schema(description = "Cibles effectivement liées")
    private List<DocumentBatchTargetRequest> linkedTargets = new ArrayList<>();

    @Schema(description = "Cibles ignorées (doublon MD5)")
    private List<DocumentBatchTargetRequest> skippedTargets = new ArrayList<>();

    @Schema(description = "Erreurs item ou par cible")
    private List<String> errors = new ArrayList<>();
}
