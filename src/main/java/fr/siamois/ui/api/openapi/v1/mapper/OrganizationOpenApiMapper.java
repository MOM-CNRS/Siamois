package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResource;
import org.springframework.stereotype.Component;

@Component
// TODO [ARCH] utiliser mapstruct
public class OrganizationOpenApiMapper {

    public OrganizationResource toResource(InstitutionDTO dto) {
        OrganizationResource r = new OrganizationResource(
                dto.getName(),
                dto.getDescription(),
                dto.getIdentifier());
        r.setResourceType("organizations");
        if (dto.getId() != null) {
            r.setId(String.valueOf(dto.getId()));
        }
        return r;
    }
}
