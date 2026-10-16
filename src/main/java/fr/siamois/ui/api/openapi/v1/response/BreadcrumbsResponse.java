package fr.siamois.ui.api.openapi.v1.response;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.breadcrumb.BreadcrumbsResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BreadcrumbsResponse extends Response<BreadcrumbsResource> {
    public BreadcrumbsResponse(BreadcrumbsResource data) {
        super(data);
    }
}
