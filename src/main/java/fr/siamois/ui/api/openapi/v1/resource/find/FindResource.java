package fr.siamois.ui.api.openapi.v1.resource.find;


import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToMany;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceIdentifier;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
@Data
@NoArgsConstructor
public class FindResource extends FindResourceIdentifier {

    private String fullIdentifier;
    protected OffsetDateTime collectionDate;
    private RelationshipToOne<ConceptResourceIdentifier> type;
    private RelationshipToOne<RecordingUnitResourceIdentifier> recordingUnit;
    private RelationshipToOne<OrganizationResourceIdentifier> organization;
    private RelationshipToMany<PersonResourceIdentifier> collectors;
    private RelationshipToMany<PersonResourceIdentifier> authors;

    private GeometryDTO geom;

}
