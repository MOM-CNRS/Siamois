package fr.siamois.ui.api.openapi.v1.response.container;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class ContainerResponse extends Response<ContainerResource> {
    public ContainerResponse(ContainerResource data) {
        super(data);
    }
}
