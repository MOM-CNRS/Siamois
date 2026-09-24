package fr.siamois.ui.api.openapi.v1.resource.place;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class PlaceResource extends PlaceResourceIdentifier {

    private String name;

    private Integer placeNumber;

    private ResolvedConceptResource type;

    private OrganizationResourceIdentifier organization;

    private GeometryDTO geom;

    // Même convention que PhaseResource/ContainerResource.answers : toujours des valeurs brutes,
    // Place n'a aucun CustomFieldAnswer (lecture/écriture réflexives directes sur SpatialUnitDTO).
    // Présent uniquement sur le détail (GET /api/v1/places/{id}) — absent sur la liste.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Valeurs des champs formulaire, indexées par fieldId (valeurs brutes). Détail seulement.")
    private Map<String, Object> answers;

    // SpatialUnit n'a pas de catalogue de types configurables (absent de ConfigurableTable) : pas
    // de GET /api/v1/projects/{id}/place-types séparé — le layout + catalogue de champs statiques
    // (SpatialUnit.DETAILS_FORM) sont attachés directement ici, détail seulement.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Gabarit du formulaire (layout). Détail seulement.")
    private FormResource formBundle;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique). Détail seulement.")
    private Map<String, FieldResource> fields;

    @JsonProperty("_permissions")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Droits du caller sur ce lieu. Détail seulement.")
    private ProjectResourcePermissions permissions;

    @Schema(description = "URI de navigation/favori du lieu", example = "/spatial-unit/42")
    private String resourceUri;

    @JsonProperty("_counts")
    private PlaceResourceCounts count;

    @JsonProperty("_links")
    private PlaceResourceLinks links;

}
