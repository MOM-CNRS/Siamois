package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResource;
import org.springframework.stereotype.Component;

@Component
public class PersonOpenApiMapper {

    public PersonResource toResource(PersonDTO dto) {
        String resourceId = dto.getId() != null ? String.valueOf(dto.getId()) : null;
        return new PersonResource(resourceId, dto.getUsername(), dto.getName(), dto.getLastname());
    }
}
