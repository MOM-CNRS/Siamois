package fr.siamois.ui.api.openapi.v1.resource.place;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceCounts;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceLinks;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
public class PlaceResource extends PlaceResourceIdentifier {

    private String name;

    private ResolvedConceptResource type;

    private OrganizationResourceIdentifier organization;

    private GeometryDTO geom;

    @JsonProperty("_counts")
    private PlaceResourceCounts count;

    @JsonProperty("_links")
    private PlaceResourceLinks links;

}
