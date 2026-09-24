package fr.siamois.ui.api.openapi.v1.response;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SiblingsResponse extends Response<SiblingsResource> {
    public SiblingsResponse(SiblingsResource data) {
        super(data);
    }
}
