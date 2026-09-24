package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mobilier OpenAPI ({@code finds}) à partir du DTO métier spécimen.
 */
@Component
@RequiredArgsConstructor
// TODO [ARCH] utiliser mapstruct
public class FindOpenApiMapper {

    private final ConceptResourceIdentifierMapper conceptResourceIdentifierMapper;
    private final PersonResourceIdentifierMapper personResourceIdentifierMapper;
    private final ProjectResponseMapper projectResponseMapper;

    public FindResource toResource(SpecimenDTO specimen) {
        FindResource r = new FindResource();
        r.setResourceType("finds");
        r.setId(specimen.getId() == null ? null : String.valueOf(specimen.getId()));
        r.setValidated(specimen.getValidated());
        r.setFullIdentifier(specimen.getFullIdentifier());
        r.setCollectionDate(specimen.getCollectionDate());
        if (specimen.getType() != null) {
            // todo: fixme
            r.setType(projectResponseMapper.toConceptFieldValue(specimen.getType(), ""));
        }
        if (specimen.getRecordingUnit() != null && specimen.getRecordingUnit().getId() != null) {
            RecordingUnitReference ru = new RecordingUnitReference();
            ru.setResourceType("recording-units");
            ru.setId(String.valueOf(specimen.getRecordingUnit().getId()));
            ru.setFullIdentifier(specimen.getRecordingUnit().getFullIdentifier());
            r.setRecordingUnit(ru);
        }
        if (specimen.getActionUnit() != null && specimen.getActionUnit().getId() != null) {
            r.setProjectId(String.valueOf(specimen.getActionUnit().getId()));
        }
        if (specimen.getCreatedByInstitution() != null && specimen.getCreatedByInstitution().getId() != null) {
            OrganizationResourceIdentifier org = new OrganizationResourceIdentifier();
            org.setResourceType("organizations");
            org.setId(String.valueOf(specimen.getCreatedByInstitution().getId()));
            r.setOrganization(org);
        }

        return r;
    }
}
