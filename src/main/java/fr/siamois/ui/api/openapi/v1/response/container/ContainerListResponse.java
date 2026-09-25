package fr.siamois.ui.api.openapi.v1.response.container;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContainerListResponse extends ListResponse<ContainerResource> {
    public ContainerListResponse(List<ContainerResource> data, ListMeta meta) {
        super(data, meta);
    }
}
