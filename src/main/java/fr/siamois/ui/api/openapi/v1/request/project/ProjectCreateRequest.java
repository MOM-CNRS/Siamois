package fr.siamois.ui.api.openapi.v1.request.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Création d'un projet (unité d'action) dans une organisation.
 */
@Data
@Schema(description = "Création d'un projet")
public class ProjectCreateRequest {

    @Schema(description = "Institution propriétaire (doit être dans le périmètre JWT).", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long organizationId;

    @Schema(description = "Nom du projet", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Identifiant court du projet dans l'organisation (base du full_identifier).", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identifier;

    @Schema(description = "Identifiant du concept de type d'opération (SIAAU.TYPE).", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long typeConceptId;

    @Schema(description = "Date de début")
    private OffsetDateTime beginDate;

    @Schema(description = "Date de fin")
    private OffsetDateTime endDate;

    @Schema(description = "Unités spatiales de contexte (spatial_unit_id) rattachées au projet.")
    private List<Long> spatialContextSpatialUnitIds;

    @Schema(description = "Valeurs du formulaire système (clés = custom_field_id, alignées sur GET /projects/form).")
    private Map<String, Object> fieldAnswers = new HashMap<>();
}
