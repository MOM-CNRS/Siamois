package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Création d'une UE : projet (unité d'action), type (concept) et valeurs par id de champ de formulaire.
 * Les clés de {@code fieldAnswers} sont les identifiants numériques de champs (comme dans GET /creation-form).
 */
@Data
@Schema(description = "Création d'une unité d'enregistrement")
public class RecordingUnitCreateRequest {

    @Schema(description = "Identifiant du projet (action_unit_id)", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long actionUnitId;

    @Schema(description = "Identifiant du concept de type d'UE (concept_id)", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long recordingUnitTypeConceptId;

    @Schema(description = "Valeurs par id de champ (string numérique) ; types alignés sur currentValue du formulaire")
    private Map<String, Object> fieldAnswers = new HashMap<>();
}
