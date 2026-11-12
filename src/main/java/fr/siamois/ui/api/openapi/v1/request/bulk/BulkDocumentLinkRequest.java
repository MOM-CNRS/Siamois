package fr.siamois.ui.api.openapi.v1.request.bulk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Corps d'une liaison (ou d'un retrait de lien) en lot entre un document et des éléments.
 */
@Data
@Schema(description = "Lie un document à chaque élément, ou retire ce lien de chacun")
public class BulkDocumentLinkRequest {

    @Schema(description = "Identifiants numériques des éléments", example = "[12, 13, 14]")
    private List<Long> ids = new ArrayList<>();

    @Schema(description = "Identifiant numérique du document", example = "88")
    private Long documentId;

    @Schema(description = "LINK : lier ; UNLINK : retirer le lien (le document n'est pas supprimé)")
    private BulkLinkMode mode;
}
