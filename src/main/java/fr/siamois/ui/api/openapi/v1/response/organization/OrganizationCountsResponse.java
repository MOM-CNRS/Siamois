package fr.siamois.ui.api.openapi.v1.response.organization;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationCountsResource;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class OrganizationCountsResponse extends Response<OrganizationCountsResource> {
    public OrganizationCountsResponse(OrganizationCountsResource data) {
        super(data);
    }
}
