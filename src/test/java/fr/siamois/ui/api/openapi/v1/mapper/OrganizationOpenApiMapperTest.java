package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourcePermissions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationOpenApiMapperTest {

    private final OrganizationOpenApiMapper mapper = new OrganizationOpenApiMapper();

    @Test
    void toResource_mapsIdentifierAndType() {
        InstitutionDTO dto = new InstitutionDTO();
        dto.setId(42L);
        dto.setName("Institut A");
        dto.setDescription("Desc");
        dto.setIdentifier("INST-A");

        OrganizationResource r = mapper.toResource(dto, new OrganizationResourcePermissions(true));

        assertThat(r.getResourceType()).isEqualTo("organizations");
        assertThat(r.getId()).isEqualTo("42");
        assertThat(r.getName()).isEqualTo("Institut A");
        assertThat(r.getDescription()).isEqualTo("Desc");
        assertThat(r.getIdentifier()).isEqualTo("INST-A");
        assertThat(r.getPermissions().canCreateProjects()).isTrue();
    }

    @Test
    void toResource_nullId_leavesIdUnset() {
        InstitutionDTO dto = new InstitutionDTO();
        dto.setId(null);
        dto.setName("X");

        OrganizationResource r = mapper.toResource(dto, new OrganizationResourcePermissions(false));

        assertThat(r.getResourceType()).isEqualTo("organizations");
        assertThat(r.getId()).isNull();
        assertThat(r.getName()).isEqualTo("X");
        assertThat(r.getPermissions().canCreateProjects()).isFalse();
    }
}
