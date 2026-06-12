package fr.siamois.ui.api.openapi.v1.response.find;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class FindFormResponse extends Response<FindMobilierFormData> {

    public FindFormResponse(FindMobilierFormData data) {
        super(data);
    }
}
