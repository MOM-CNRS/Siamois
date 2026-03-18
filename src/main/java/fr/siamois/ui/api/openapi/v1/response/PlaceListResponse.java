package fr.siamois.ui.api.openapi.v1.response;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PlaceListResponse extends ListResponse<PlaceResource> {
    public PlaceListResponse(List<PlaceResource> data, ListMeta meta) {
        super(data, meta);
    }
}
