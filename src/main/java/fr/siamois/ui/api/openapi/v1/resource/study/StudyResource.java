package fr.siamois.ui.api.openapi.v1.resource.study;


import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.ui.api.openapi.v1.resource.BookmarkableResource;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Étude d'un projet")
public class StudyResource implements BookmarkableResource {

    @Schema(description = "Type de ressource", example = "studies")
    private String resourceType = "studies";

    @Schema(description = "Identifiant technique (study_id)")
    private String id;

    @Schema(description = "Identifiant métier de l'étude")
    private String identifier;

    @Schema(description = "Identifiant du projet (unité d'action) auquel appartient cette étude")
    private String projectId;

    // Organization-wide list only (GET /api/v1/<collection>?organizationId=…): the row's project,
    // for the list's "Projet" column. Absent everywhere else (every row there shares one project).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Projet de rattachement (liste d'organisation seulement)")
    private fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef project;

    @Schema(description = "Organisation propriétaire de l'étude (son institution de création) — ce "
            + "que la fiche React résout son propre catalogue de concepts contre "
            + "(GET /api/v1/organizations/{id}/concepts), même convention que PhaseResource.organization.")
    private OrganizationResourceIdentifier organization;

    private ResolvedConceptResource type;

    // Même convention que PhaseResource.answers : toujours des valeurs brutes (liste ET détail),
    // Study n'a jamais de CustomFieldAnswer (voir StudyAnswersProjector's own javadoc).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Valeurs des champs formulaire, indexées par fieldId (valeurs brutes).")
    private Map<String, Object> answers;

    @JsonProperty("_permissions")
    @Schema(description = "Droits du caller sur cette étude")
    private ProjectResourcePermissions permissions;

    @Schema(description = "URI de navigation/favori de l'étude", example = "/study/42")
    private String resourceUri;

    @Schema(description = "L'étude est dans les favoris du caller")
    private boolean bookmarked;

    @Schema(description = "Statut de validation : INCOMPLETE (en cours), COMPLETE (terminé), VALIDATED (validé), CANCELLED (annulé)")
    private ValidationStatus validationStatus;
}
