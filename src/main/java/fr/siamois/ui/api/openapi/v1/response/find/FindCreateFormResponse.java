package fr.siamois.ui.api.openapi.v1.response.find;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.find.FindCreateFormData;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class FindCreateFormResponse extends Response<FindCreateFormData> {

    public FindCreateFormResponse(FindCreateFormData data) {
        super(data);
    }
}
