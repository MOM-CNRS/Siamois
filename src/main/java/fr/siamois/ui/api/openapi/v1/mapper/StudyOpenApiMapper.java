package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.StudyDTO;
import fr.siamois.ui.api.openapi.v1.resource.study.StudyResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Study OpenAPI ({@code studies}) à partir du DTO métier — pendant plat de
 * {@link PhaseOpenApiMapper} (pas MapStruct, mêmes raisons).
 */
@Component
@RequiredArgsConstructor
public class StudyOpenApiMapper {

    private final ProjectResponseMapper projectResponseMapper;

    public StudyResource toResource(StudyDTO study, String lang, Map<Long, String> resolvedLabels) {
        StudyResource r = new StudyResource();
        r.setResourceType("studies");
        r.setId(study.getId() != null ? String.valueOf(study.getId()) : null);
        r.setValidationStatus(study.getValidationStatus());
        r.setIdentifier(study.getIdentifier());
        if (study.getActionUnit() != null && study.getActionUnit().getId() != null) {
            r.setProjectId(String.valueOf(study.getActionUnit().getId()));
        }
        if (study.getActionUnit() != null && study.getActionUnit().getCreatedByInstitution() != null
                && study.getActionUnit().getCreatedByInstitution().getId() != null) {
            OrganizationResourceIdentifier org = new OrganizationResourceIdentifier();
            org.setResourceType("organizations");
            org.setId(String.valueOf(study.getActionUnit().getCreatedByInstitution().getId()));
            r.setOrganization(org);
        }
        if (study.getType() != null) {
            r.setType(projectResponseMapper.toConceptFieldValue(study.getType(), lang, resolvedLabels));
        }
        if (study.getId() != null) {
            r.setResourceUri("/study/" + study.getId());
        }
        return r;
    }
}
