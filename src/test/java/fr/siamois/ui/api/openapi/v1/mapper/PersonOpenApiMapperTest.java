package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonOpenApiMapperTest {

    private final PersonOpenApiMapper mapper = new PersonOpenApiMapper();

    @Test
    void toResource_mapsAllFields() {
        PersonDTO dto = new PersonDTO();
        dto.setId(42L);
        dto.setUsername("alice");
        dto.setName("Alice");
        dto.setLastname("Martin");

        PersonResource resource = mapper.toResource(dto);

        assertThat(resource.id()).isEqualTo("42");
        assertThat(resource.username()).isEqualTo("alice");
        assertThat(resource.name()).isEqualTo("Alice");
        assertThat(resource.lastname()).isEqualTo("Martin");
    }

    @Test
    void toResource_nullId_returnsNullResourceId() {
        PersonDTO dto = new PersonDTO();
        dto.setUsername("bob");

        PersonResource resource = mapper.toResource(dto);

        assertThat(resource.id()).isNull();
        assertThat(resource.username()).isEqualTo("bob");
    }
}
