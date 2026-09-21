package fr.siamois.ui.api.openapi.v1.resource.project;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceLightResource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class ProjectResource extends ProjectResourceIdentifier {

    @Schema(description = "Nom du projet")
    private String name;

    @Schema(description = "Identifiant complet du projet")
    private String fullIdentifier;

    @Schema(description = "Identifiant du projet")
    private String identifier;

    @Schema(description = "Date de début d'un projet")
    private OffsetDateTime beginDate;

    @Schema(description = "Date de fin d'un projet")
    private OffsetDateTime endDate;

    private ResolvedConceptResource type;

    @Schema(description = "Localisation principale / commune du projet")
    private PlaceLightResource mainLocation;

    @Schema(description = "Localisations précises du projet")
    private List<PlaceLightResource> spatialContext;

    @Schema(description = "Organisation d'appartenance du projet")
    private OrganizationResourceIdentifier organization;

    @Schema(description = "Emprise du projet (GeoJSON)")
    private GeometryDTO geom;

    @JsonProperty("_counts")
    private ProjectResourceCounts count;

    @JsonProperty("_links")
    private ProjectResourceLinks links;

    @JsonProperty("_permissions")
    @Schema(description = "Droits du caller sur ce projet")
    private ProjectResourcePermissions permissions;

    @Schema(description = "Le projet est dans les favoris du caller")
    private boolean bookmarked;

    /**
     * URI de la ressource telle que stockée par les favoris et utilisée par la navigation JSF
     * ({@code /action-unit/{id}}). Exposée pour que le front n'ait pas à reconstruire ce préfixe en dur.
     */
    @Schema(description = "URI de navigation/favori du projet", example = "/action-unit/42")
    private String resourceUri;

    /**
     * Réponses aux champs de formulaire, par id de champ — valeurs brutes, sans enveloppe
     * {@code FieldAnswer} : scalaire pour TEXT/INTEGER/DECIMAL/DATETIME, {@code ResourceRef} pour les
     * {@code SELECT_ONE_*}, liste de {@code ResourceRef} pour les {@code SELECT_MULTIPLE_*}. Les
     * métadonnées des champs (libellé, type de réponse, liaison) viennent du catalogue de
     * {@code GET /api/v1/organizations/{id}/project-types}, pas d'ici.
     *
     * <p>Présent uniquement si l'appel a demandé une projection via {@code ?fields=} ; absent sinon,
     * pour que la liste par défaut reste aussi légère qu'avant.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Valeurs des champs de formulaire demandés via ?fields=, par id de champ")
    private Map<String, Object> answers;

}