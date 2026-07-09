package fr.siamois.ui.api.openapi.v1.request.find;

import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
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
    private Map<String, AnswerInput> fieldAnswers = new HashMap<>();
}
