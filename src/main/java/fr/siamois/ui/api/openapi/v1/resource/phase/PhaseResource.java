package fr.siamois.ui.api.openapi.v1.resource.phase;

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
@Schema(description = "Phase chronologique d'un projet")
public class PhaseResource implements BookmarkableResource {

    @Schema(description = "Type de ressource", example = "phases")
    private String resourceType = "phases";

    @Schema(description = "Identifiant technique (phase_id)")
    private String id;

    @Schema(description = "Identifiant métier de la phase")
    private String identifier;

    @Schema(description = "Titre de la phase")
    private String title;

    @Schema(description = "Libellé d'affichage (titre ou identifiant)")
    private String label;

    @Schema(description = "Identifiant du projet (unité d'action) auquel appartient cette phase")
    private String projectId;

    // Organization-wide list only (GET /api/v1/<collection>?organizationId=…): the row's project,
    // for the list's "Projet" column. Absent everywhere else (every row there shares one project).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Projet de rattachement (liste d'organisation seulement)")
    private fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef project;

    @Schema(description = "Organisation propriétaire de la phase (son institution de création) — ce que "
            + "la fiche React résout son propre catalogue de concepts contre "
            + "(GET /api/v1/organizations/{id}/concepts), même convention que RecordingUnitResource.organization.")
    private OrganizationResourceIdentifier organization;

    private ResolvedConceptResource type;

    // Same convention as FindResource/RecordingUnitResource.answers — raw values on the list,
    // same raw values on the detail too (Phase has no CustomFormResponseViewModel involvement at
    // all, see PhaseAnswersProjector's own javadoc: every field is a plain reflective read/write
    // on PhaseDTO, so there is no separate FieldAnswer-enveloped shape to produce here).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Valeurs des champs formulaire, indexées par fieldId (valeurs brutes).")
    private Map<String, Object> answers;

    @JsonProperty("_permissions")
    @Schema(description = "Droits du caller sur cette phase")
    private ProjectResourcePermissions permissions;

    @Schema(description = "URI de navigation/favori de la phase", example = "/phase/42")
    private String resourceUri;

    @Schema(description = "La phase est dans les favoris du caller")
    private boolean bookmarked;
}
