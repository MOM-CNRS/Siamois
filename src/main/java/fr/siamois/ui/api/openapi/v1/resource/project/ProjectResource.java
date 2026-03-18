package fr.siamois.ui.api.openapi.v1.resource.project;


import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.dto.entity.*;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipCountOnly;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToMany;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.generic.response.ResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
public class ProjectResource extends ProjectResourceIdentifier {

    private String name;

    private String identifier;
    private String recordingUnitIdentifierFormat;
    private String fullIdentifier;
    private Integer maxRecordingUnitCode;
    private Integer minRecordingUnitCode;
    private String recordingUnitIdentifierLang;
    private OffsetDateTime beginDate;
    private OffsetDateTime endDate;

    private RelationshipToOne<ConceptResourceIdentifier> type;

    private RelationshipToOne<fr.siamois.ui.api.openapi.v1.resource.project.PlaceResourceIdentifier> mainLocation ;
    private RelationshipToMany<fr.siamois.ui.api.openapi.v1.resource.project.PlaceResourceIdentifier> spatialContext ;
    private RelationshipToOne<OrganizationResourceIdentifier> organization;
    private RelationshipCountOnly children;
    private RelationshipCountOnly recordingUnitList;

}