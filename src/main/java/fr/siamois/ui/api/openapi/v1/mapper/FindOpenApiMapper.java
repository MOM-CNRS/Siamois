package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToMany;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceIdentifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mobilier OpenAPI ({@code finds}) à partir du DTO métier spécimen.
 */
@Component
@RequiredArgsConstructor
public class FindOpenApiMapper {

    private final ConceptResourceIdentifierMapper conceptResourceIdentifierMapper;
    private final PersonResourceIdentifierMapper personResourceIdentifierMapper;

    public FindResource toResource(SpecimenDTO specimen) {
        FindResource r = new FindResource();
        r.setResourceType("finds");
        r.setId(specimen.getId() == null ? null : String.valueOf(specimen.getId()));
        r.setFullIdentifier(specimen.getFullIdentifier());
        r.setCollectionDate(specimen.getCollectionDate());
        if (specimen.getType() != null) {
            r.setType(new RelationshipToOne<>(conceptResourceIdentifierMapper.convert(specimen.getType())));
        }
        if (specimen.getRecordingUnit() != null && specimen.getRecordingUnit().getId() != null) {
            RecordingUnitResourceIdentifier ru = new RecordingUnitResourceIdentifier();
            ru.setResourceType("recording-units");
            ru.setId(String.valueOf(specimen.getRecordingUnit().getId()));
            r.setRecordingUnit(new RelationshipToOne<>(ru));
        }
        if (specimen.getCreatedByInstitution() != null && specimen.getCreatedByInstitution().getId() != null) {
            OrganizationResourceIdentifier org = new OrganizationResourceIdentifier();
            org.setResourceType("organizations");
            org.setId(String.valueOf(specimen.getCreatedByInstitution().getId()));
            r.setOrganization(new RelationshipToOne<>(org));
        }
        if (specimen.getCollectors() != null && !specimen.getCollectors().isEmpty()) {
            List<PersonResourceIdentifier> list = specimen.getCollectors().stream()
                    .map(personResourceIdentifierMapper::convert)
                    .toList();
            r.setCollectors(new RelationshipToMany<>(list));
        }
        if (specimen.getAuthors() != null && !specimen.getAuthors().isEmpty()) {
            List<PersonResourceIdentifier> list = specimen.getAuthors().stream()
                    .map(personResourceIdentifierMapper::convert)
                    .toList();
            r.setAuthors(new RelationshipToMany<>(list));
        }
        r.setGeom(null);
        return r;
    }
}
