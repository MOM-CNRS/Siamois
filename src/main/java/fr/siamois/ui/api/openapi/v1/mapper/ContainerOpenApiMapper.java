package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Container OpenAPI ({@code containers}) à partir du DTO métier — pendant plat de
 * {@link PhaseOpenApiMapper} (pas MapStruct, mêmes raisons).
 */
@Component
@RequiredArgsConstructor
public class ContainerOpenApiMapper {

    private final ProjectResponseMapper projectResponseMapper;

    public ContainerResource toResource(ContainerDTO container, String lang, Map<Long, String> resolvedLabels) {
        ContainerResource r = new ContainerResource();
        r.setResourceType("containers");
        r.setId(container.getId() != null ? String.valueOf(container.getId()) : null);
        r.setIdentifier(container.getIdentifier());
        if (container.getActionUnit() != null && container.getActionUnit().getId() != null) {
            r.setProjectId(String.valueOf(container.getActionUnit().getId()));
        }
        if (container.getActionUnit() != null && container.getActionUnit().getCreatedByInstitution() != null
                && container.getActionUnit().getCreatedByInstitution().getId() != null) {
            OrganizationResourceIdentifier org = new OrganizationResourceIdentifier();
            org.setResourceType("organizations");
            org.setId(String.valueOf(container.getActionUnit().getCreatedByInstitution().getId()));
            r.setOrganization(org);
        }
        if (container.getType() != null) {
            r.setType(projectResponseMapper.toConceptFieldValue(container.getType(), lang, resolvedLabels));
        }
        if (container.getId() != null) {
            r.setResourceUri("/container/" + container.getId());
        }
        return r;
    }
}
