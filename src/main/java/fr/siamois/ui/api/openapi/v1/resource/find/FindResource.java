package fr.siamois.ui.api.openapi.v1.resource.find;

import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipCountOnly;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToMany;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceIdentifier;

import java.time.OffsetDateTime;
import java.util.List;

public class FindResource extends FindResourceIdentifier {

    private String fullIdentifier;
    protected OffsetDateTime collectionDate;
    private RelationshipToOne<ConceptResourceIdentifier> type;
    private RelationshipToOne<RecordingUnitResourceIdentifier> recordingUnit;
    private RelationshipToOne<OrganizationResourceIdentifier> organization;
    private RelationshipToMany<PersonResourceIdentifier> collectors;
    private RelationshipToMany<PersonResourceIdentifier> authors;

}
