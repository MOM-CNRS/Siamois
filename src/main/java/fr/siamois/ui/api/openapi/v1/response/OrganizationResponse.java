package fr.siamois.ui.api.openapi.v1.response;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrganizationResponse extends Response<OrganizationResource> {
    public OrganizationResponse(OrganizationResource data) {
        super(data);
    }
}

