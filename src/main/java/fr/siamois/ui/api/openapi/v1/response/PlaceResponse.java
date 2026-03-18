package fr.siamois.ui.api.openapi.v1.response;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PlaceResponse extends Response<PlaceResource> {
    public PlaceResponse(PlaceResource data) {
        super(data);
    }
}

