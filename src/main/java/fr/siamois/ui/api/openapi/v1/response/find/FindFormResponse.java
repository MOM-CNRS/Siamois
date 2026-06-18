package fr.siamois.ui.api.openapi.v1.response.find;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.find.FindFormData;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class FindFormResponse extends Response<FindFormData> {

    public FindFormResponse(FindFormData data) {
        super(data);
    }
}
