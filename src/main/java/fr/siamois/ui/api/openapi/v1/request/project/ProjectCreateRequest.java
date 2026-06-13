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

    @Schema(description = "Organisation parente du projet", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private String organizationId;

    @Schema(description = "Nom du projet", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Identifiant unique du projet dans l'organisation", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identifier;

    @Schema(description = "Identifiant du concept du type de projet", requiredMode = Schema.RequiredMode.REQUIRED)
    private String typeId;

    @Schema(description = "Date de début")
    private OffsetDateTime beginDate;

    @Schema(description = "Date de fin")
    private OffsetDateTime endDate;

    @Schema(description = "Contexte spatiale du projet (list d'identifiants unité spatiale, ex: liste des parcelles")
    private List<String> spatialContextIds;

    @Schema(description = "Localisation principale du projet (Identifiant d'unité spatiale)")
    private String mainLocationId;

    // No additional value for creation for now

}
