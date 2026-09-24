package fr.siamois.ui.api.openapi.v1.request.container;

import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Mise à jour partielle d'un contenant : seules les entrées présentes dans {@code answers} sont
 * fusionnées. Même clé de champ que {@code GET /api/v1/containers/{id}}.
 */
@Data
@Schema(description = "Mise à jour partielle d'un contenant (réponses formulaire)")
public class ContainerPatchRequest {

    @Schema(description = "Valeurs par fieldId ({ value } / { values })")
    private Map<String, AnswerInput> answers = new HashMap<>();

}
