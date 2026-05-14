package fr.siamois.ui.api.openapi.v1.request.find;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Création d'un mobilier (spécimen) sur une UE : type (concept) et valeurs par id de champ (comme GET /finds/{id}/form).
 */
@Data
@Schema(description = "Création d'un mobilier")
public class FindCreateRequest {

    @Schema(description = "Identifiant numérique de l'unité d'enregistrement (recording_unit_id)", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long recordingUnitId;

    @Schema(description = "Identifiant du concept de type de mobilier (concept_id)", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long specimenTypeConceptId;

    @Schema(description = "Valeurs par id de champ (string numérique)")
    private Map<String, Object> fieldAnswers = new HashMap<>();
}
