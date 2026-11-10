package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResource;
import org.springframework.stereotype.Component;

@Component
public class PersonOpenApiMapper {

    /**
     * Maps a person holding an account (active, or invited by e-mail).
     *
     * @param dto the person
     * @return the API resource of the person
     */
    public PersonResource toResource(PersonDTO dto) {
        return toResource(dto, false);
    }

    /**
     * Maps a person, stating whether they still await the creation of their account.
     *
     * @param dto            the person
     * @param accountPending {@code true} for a person created from their name only, without account yet
     * @return the API resource of the person
     */
    public PersonResource toResource(PersonDTO dto, boolean accountPending) {
        String resourceId = dto.getId() != null ? String.valueOf(dto.getId()) : null;
        return new PersonResource(resourceId, dto.getUsername(), dto.getName(), dto.getLastname(), accountPending);
    }
}
