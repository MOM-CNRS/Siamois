package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.Map;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class RecordingUnitResource extends RecordingUnitResourceIdentifier {

    @Schema(description = "Révision de synchronisation (optimistic locking)")
    private Long syncRevision;

    private String identifier;
    private String fullIdentifier;
    private String projectId;

    // Organization-wide list only (GET /api/v1/<collection>?organizationId=…): the row's project,
    // for the list's "Projet" column. Absent everywhere else (every row there shares one project).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Projet de rattachement (liste d'organisation seulement)")
    private fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef project;

    @Schema(description = "Organisation propriétaire de l'unité (son institution de création) — ce que "
            + "le fiche React résout son propre catalogue de concepts contre (GET /api/v1/organizations/"
            + "{id}/concepts), faute d'un projectId suffisant côté client pour le dériver autrement.")
    private OrganizationResourceIdentifier organization;

    private ResolvedConceptResource type;

    @Schema(description = "Géométrie de l'UE.")
    @Nullable
    private GeometryDTO geom;

    // Two shapes share this one field, by endpoint: the detail (buildMobileDetail) sets it to
    // Map<String, FieldAnswer> — each entry embeds its own field definition, since the detail is
    // the one place a client can't have fetched a catalog first. The list
    // (GET /api/v1/projects/{id}/recording-units?fields=…) sets it to raw values instead, exactly
    // like ProjectResource.answers — the client already has the field catalog from
    // GET /api/v1/projects/{id}/recording-unit-types, so shipping the definition again on every row
    // would be pure overhead. fields/types.ts's unwrapAnswer already handles both on the client.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Valeurs des champs formulaire, indexées par fieldId. Sur le détail, chaque entrée "
            + "embarque sa définition (enveloppe FieldAnswer) ; sur la liste, valeurs brutes comme "
            + "ProjectResource.answers — le catalogue de champs est à récupérer séparément.")
    private Map<String, Object> answers;

    @JsonProperty("_counts")
    private RecordingUnitResourceCounts count;

    @JsonProperty("_links")
    private RecordingUnitResourceLinks links;

    @JsonProperty("_permissions")
    @Schema(description = "Droits du caller sur cette unité d'enregistrement. Sur la liste "
            + "(GET /api/v1/projects/{id}/recording-units), un booléen par page puisque toutes les lignes "
            + "partagent le même projet ; sur le détail (GET /api/v1/recording-units/{id}), calculé pour "
            + "cette seule unité.")
    private ProjectResourcePermissions permissions;

}
