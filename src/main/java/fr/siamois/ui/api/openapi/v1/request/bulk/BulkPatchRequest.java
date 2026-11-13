package fr.siamois.ui.api.openapi.v1.request.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Corps d'une modification en lot : la même mise à jour partielle appliquée à chaque élément.
 */
@Data
@Schema(description = "Modification en lot : la même mise à jour partielle appliquée à chaque élément")
public class BulkPatchRequest {

    @Schema(description = "Identifiants numériques des éléments à modifier", example = "[12, 13, 14]")
    private List<Long> ids = new ArrayList<>();

    @Schema(description = "Mise à jour partielle, au format du PATCH unitaire de la collection "
            + "(ex. { \"answers\": { \"<fieldId>\": { \"add\": [42] } } }). "
            + "Un identifiant étant unique par élément, `identifier` est refusé.")
    private JsonNode patch;
}
