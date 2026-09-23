package fr.siamois.ui.api.openapi.v1.request.phase;

import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Mise à jour partielle d'une phase : seules les entrées présentes dans {@code answers} sont
 * fusionnées. Même clé de champ que {@code GET /api/v1/phases/{id}}.
 */
@Data
@Schema(description = "Mise à jour partielle d'une phase (réponses formulaire)")
public class PhasePatchRequest {

    @Schema(description = "Valeurs par fieldId ({ value } / { values })")
    private Map<String, AnswerInput> answers = new HashMap<>();

}
