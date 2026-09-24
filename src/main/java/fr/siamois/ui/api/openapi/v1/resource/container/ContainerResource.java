package fr.siamois.ui.api.openapi.v1.resource.container;

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
@Schema(description = "Contenant d'un projet")
public class ContainerResource implements BookmarkableResource {

    @Schema(description = "Type de ressource", example = "containers")
    private String resourceType = "containers";

    @Schema(description = "Identifiant technique (container_id)")
    private String id;

    @Schema(description = "Identifiant métier du contenant")
    private String identifier;

    @Schema(description = "Identifiant du projet (unité d'action) auquel appartient ce contenant")
    private String projectId;

    // Organization-wide list only (GET /api/v1/<collection>?organizationId=…): the row's project,
    // for the list's "Projet" column. Absent everywhere else (every row there shares one project).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Projet de rattachement (liste d'organisation seulement)")
    private fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef project;

    @Schema(description = "Organisation propriétaire du contenant (son institution de création) — ce "
            + "que la fiche React résout son propre catalogue de concepts contre "
            + "(GET /api/v1/organizations/{id}/concepts), même convention que PhaseResource.organization.")
    private OrganizationResourceIdentifier organization;

    private ResolvedConceptResource type;

    // Même convention que PhaseResource.answers : toujours des valeurs brutes (liste ET détail),
    // Container n'a jamais de CustomFieldAnswer (voir ContainerAnswersProjector's own javadoc).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Valeurs des champs formulaire, indexées par fieldId (valeurs brutes).")
    private Map<String, Object> answers;

    @JsonProperty("_permissions")
    @Schema(description = "Droits du caller sur ce contenant")
    private ProjectResourcePermissions permissions;

    @Schema(description = "URI de navigation/favori du contenant", example = "/container/42")
    private String resourceUri;

    @Schema(description = "Le contenant est dans les favoris du caller")
    private boolean bookmarked;
}
