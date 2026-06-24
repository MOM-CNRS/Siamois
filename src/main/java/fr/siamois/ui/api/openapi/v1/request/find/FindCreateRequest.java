package fr.siamois.ui.api.openapi.v1.request.find;

import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Création d'un mobilier (spécimen) sur une UE : type (concept) et valeurs par id de champ
 * (clés = identifiants custom_field).
 */
@Data
@Schema(description = "Création d'un mobilier")
public class FindCreateRequest {

    @Schema(
            description = "Clé d'UE : identifiant numérique (recording_unit_id).",
            example = "187",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String recordingUnitId;

    @Schema(
            description = "Identifiant du concept de type de mobilier (concept_id, chaîne numérique).",
            example = "42",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String typeId;

    @Schema(description = "Valeurs par id de champ (string numérique)")
    private Map<String, AnswerInput> fieldAnswers = new HashMap<>();
}
