package fr.siamois.ui.api.openapi.v1.response.organization;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourcePermissions;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class OrganizationPermissionsResponse extends Response<OrganizationResourcePermissions> {
    public OrganizationPermissionsResponse(OrganizationResourcePermissions data) {
        super(data);
    }
}
