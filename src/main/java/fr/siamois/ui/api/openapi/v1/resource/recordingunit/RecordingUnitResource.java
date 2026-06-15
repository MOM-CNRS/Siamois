package fr.siamois.ui.api.openapi.v1.resource.recordingunit;


import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;


@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class RecordingUnitResource
        extends RecordingUnitResourceIdentifier {

    @Schema(description = "Révision de synchronisation (optimistic locking)")
    private Long syncRevision;

    private String identifier;
    private String fullIdentifier;

    private OffsetDateTime openingDate;
    private OffsetDateTime closingDate;

    private String description;
    private GeometryDTO geom;

    private ResolvedConceptResource type;
    private PersonResourceIdentifier author;
    private List<PersonResourceIdentifier> contributors;

    private PlaceResourceIdentifier place;

    @Schema(description = "Couleur de la matrice (libellé ou code selon saisie)")
    private String matrixColor;

    private OrganizationResourceIdentifier organization;

    private ProjectResourceIdentifier project;

}