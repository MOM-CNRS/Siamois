package fr.siamois.ui.api.openapi.v1.resource.place;

import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
public class PlaceResource extends PlaceResourceIdentifier {

    private String name;

    private ConceptResourceIdentifier type;

    private Long children;
    private Long recordingUnitList;
    private OrganizationResourceIdentifier organization;
    private Long relatedActionUnitList;

    private GeometryDTO geom;

}
