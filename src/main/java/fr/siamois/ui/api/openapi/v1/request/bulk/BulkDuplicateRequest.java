package fr.siamois.ui.api.openapi.v1.request.bulk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Corps d'une duplication en lot : les éléments de la collection à dupliquer, chacun avec ses relations, sans ses enfants.
 */
@Data
@Schema(description = "Duplication en lot : chaque élément est copié avec ses relations, sans ses enfants")
public class BulkDuplicateRequest {

    @Schema(description = "Identifiants numériques des éléments à dupliquer", example = "[12, 13, 14]")
    private List<Long> ids = new ArrayList<>();
}
