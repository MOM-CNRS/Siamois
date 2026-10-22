package fr.siamois.ui.api.openapi.v1.request.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "Manifeste d'upload massif de documents")
public class DocumentBatchManifestRequest {

    @Schema(description = "Items du lot (index 0 → part file_0, etc.)", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DocumentBatchItemRequest> items = new ArrayList<>();
}
