package fr.siamois.ui.api.openapi.v1.response.find;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class FindFormResponse extends Response<FindResource> {

    public FindFormResponse(FindResource data) {
        super(data);
    }
}
