package fr.siamois.ui.api.openapi.v1.resource.place;

import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipCountOnly;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.PlaceResourceIdentifier;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
public class PlaceResource extends PlaceResourceIdentifier {

    private String name;

    private RelationshipToOne<ConceptResourceIdentifier> type;

    private RelationshipCountOnly children;
    private RelationshipCountOnly recordingUnitList;
    private RelationshipToOne<OrganizationResourceIdentifier> organization;
    private RelationshipCountOnly relatedActionUnitList;

    private GeometryDTO geom;

}
