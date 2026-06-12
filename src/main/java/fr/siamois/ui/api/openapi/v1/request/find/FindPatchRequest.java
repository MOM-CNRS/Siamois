package fr.siamois.ui.api.openapi.v1.request.find;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Mise à jour partielle : seules les entrées présentes dans {@code fieldAnswers} sont fusionnées.
 */
@Data
@Schema(description = "Mise à jour partielle d'un mobilier (réponses formulaire)")
public class FindPatchRequest {

    @Schema(description = "Valeurs par id de champ (string numérique)")
    private Map<String, Object> fieldAnswers = new HashMap<>();
}
