package fr.siamois.ui.api.openapi.v1.response;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrganizationResponse extends Response<OrganizationResource> {
    public OrganizationResponse(OrganizationResource data) {
        super(data);
    }
}

