package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipCountOnly;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToMany;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourceIdentifier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.locationtech.jts.geom.Geometry;

import java.time.OffsetDateTime;


@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class RecordingUnitResource
        extends RecordingUnitResourceIdentifier {

    private String identifier;
    private String fullIdentifier;

    private OffsetDateTime openingDate;
    private OffsetDateTime closingDate;

    private String description;
    private GeometryDTO geom;


    private RelationshipToOne<ConceptResourceIdentifier> type;
    private RelationshipToMany<PersonResourceIdentifier> contributors;
    private RelationshipCountOnly specimen;
    private RelationshipToOne<OrganizationResourceIdentifier> organization;
    private RelationshipToOne<ProjectResourceIdentifier> project;

}