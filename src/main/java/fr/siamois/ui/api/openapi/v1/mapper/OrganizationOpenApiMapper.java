package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourcePermissions;
import org.springframework.stereotype.Component;

@Component
// TODO [ARCH] utiliser mapstruct
public class OrganizationOpenApiMapper {

    /**
     * @param permissions {@code _permissions} block, computed by the caller (list endpoints batch it
     *                    across the whole page via {@code institutionIdsWithActionUnitCreatePermission}
     *                    rather than one permission query per row — this mapper stays a pure DTO->resource
     *                    step, same as {@link ProjectResponseMapper} does for {@code ProjectResource})
     */
    public OrganizationResource toResource(InstitutionDTO dto, OrganizationResourcePermissions permissions) {
        OrganizationResource r = new OrganizationResource(
                dto.getName(),
                dto.getDescription(),
                dto.getIdentifier(),
                permissions);
        r.setResourceType("organizations");
        if (dto.getId() != null) {
            r.setId(String.valueOf(dto.getId()));
        }
        return r;
    }
}
