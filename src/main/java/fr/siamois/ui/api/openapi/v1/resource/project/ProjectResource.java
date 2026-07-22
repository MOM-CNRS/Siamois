package fr.siamois.ui.api.openapi.v1.resource.project;


import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceLightResource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

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

    @Schema(description = "Localisation ptincipale d'un projet")
    private PlaceLightResource mainLocation ;

    @Schema(description = "Organisation d'appartenance du projet")
    private OrganizationResourceIdentifier organization;

    @JsonProperty("_counts")
    private ProjectResourceCounts count;

    @JsonProperty("_links")
    private ProjectResourceLinks links;

}