package fr.siamois.ui.api.openapi.v1.resource.find;

import fr.siamois.ui.api.openapi.v1.resource.BookmarkableResource;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.PointDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitReference;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
public class FindResource extends FindResourceIdentifier implements BookmarkableResource {

    private String fullIdentifier;
    protected OffsetDateTime collectionDate;
    private ResolvedConceptResource type;
    private RecordingUnitReference recordingUnit;
    private OrganizationResourceIdentifier organization;

    @Schema(description = "Identifiant du projet (unité d'action) auquel appartient ce mobilier — ce que "
            + "la fiche React résout son propre catalogue de types contre (GET /api/v1/projects/{id}/"
            + "find-types), même convention que RecordingUnitResource.projectId.")
    private String projectId;

    // Organization-wide list only (GET /api/v1/<collection>?organizationId=…): the row's project,
    // for the list's "Projet" column. Absent everywhere else (every row there shares one project).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Projet de rattachement (liste d'organisation seulement)")
    private fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef project;

    @Schema(description = "Localisation de découverte du mobilier")
    @Nullable
    private PointDTO geom;

    // Two shapes share this one field, by endpoint — same convention as
    // RecordingUnitResource.answers: the detail (buildFindMobilierForm) sets it to a
    // Map<String, FieldAnswer> (each entry embeds its own field definition), the list
    // (GET /api/v1/projects/{id}/mobiliers) would set raw values instead. fields/types.ts's
    // unwrapAnswer already handles both on the client.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Valeurs des champs formulaire, indexées par fieldId. Sur le détail, chaque "
            + "entrée embarque sa définition (enveloppe FieldAnswer) ; sur la liste, valeurs brutes.")
    private Map<String, Object> answers;

    @JsonProperty("_permissions")
    @Schema(description = "Droits du caller sur ce mobilier")
    private ProjectResourcePermissions permissions;

    @Schema(description = "URI de navigation/favori du mobilier", example = "/specimen/42")
    private String resourceUri;

    @Schema(description = "Le mobilier est dans les favoris du caller")
    private boolean bookmarked;

}
