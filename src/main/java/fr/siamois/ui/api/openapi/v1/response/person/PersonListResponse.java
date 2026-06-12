package fr.siamois.ui.api.openapi.v1.response.person;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PersonListResponse extends ListResponse<PersonResource> {

    public PersonListResponse(List<PersonResource> data, ListMeta meta) {
        super(data, meta);
    }
}
