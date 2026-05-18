package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResource;
import org.springframework.stereotype.Component;

@Component
public class PersonOpenApiMapper {

    public PersonResource toResource(PersonDTO dto) {
        PersonResource r = new PersonResource(dto.getName(), dto.getLastname(), dto.getEmail(), dto.getUsername());
        if (dto.getId() != null) {
            r.setId(String.valueOf(dto.getId()));
        }
        return r;
    }
}
