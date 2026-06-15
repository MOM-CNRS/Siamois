package fr.siamois.ui.api.openapi.v1.resource.find;


import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceIdentifier;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class FindResource extends FindResourceIdentifier {

    private String fullIdentifier;
    protected OffsetDateTime collectionDate;
    private ResolvedConceptResource type;
    private RecordingUnitResourceIdentifier recordingUnit;
    private OrganizationResourceIdentifier organization;
    private List<PersonResourceIdentifier> collectors;
    private List<PersonResourceIdentifier>  authors;
    private GeometryDTO geom;

}
