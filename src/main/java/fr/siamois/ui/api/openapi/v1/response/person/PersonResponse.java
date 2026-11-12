package fr.siamois.ui.api.openapi.v1.response.person;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Une personne, enveloppée dans {@code data}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonResponse extends Response<PersonResource> {

    /**
     * @param data la personne
     */
    public PersonResponse(PersonResource data) {
        super(data);
    }
}
