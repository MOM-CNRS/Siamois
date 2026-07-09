package fr.siamois.ui.api.openapi.v1.response.spatialunit;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = true)
public class PlaceAutocompleteListResponse extends ListResponse<PlaceAutocompleteItemApi> {

    public PlaceAutocompleteListResponse(List<PlaceAutocompleteItemApi> data, ListMeta meta) {
        super(data, meta);
    }
}
