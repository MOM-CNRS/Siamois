package fr.siamois.ui.api.openapi.v1.request.bulk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Corps d'une suppression en lot : les éléments de la collection à supprimer.
 */
@Data
@Schema(description = "Suppression en lot d'éléments d'une même collection")
public class BulkDeleteRequest {

    @Schema(description = "Identifiants numériques des éléments à supprimer", example = "[12, 13, 14]")
    private List<Long> ids = new ArrayList<>();
}
