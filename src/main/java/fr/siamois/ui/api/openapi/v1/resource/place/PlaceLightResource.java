package fr.siamois.ui.api.openapi.v1.resource.place;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PlaceLightResource extends PlaceResourceIdentifier {

    private String name;

}
