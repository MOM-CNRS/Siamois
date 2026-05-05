package fr.siamois.ui.api.openapi.v1.resource.project;


import fr.siamois.ui.api.openapi.v1.generic.response.ResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PlaceResourceIdentifier implements ResourceIdentifier {

    @Schema(description = "Resource type",
            example = "places",
            allowableValues = {"places"})
    private String resourceType;

    private String id;


}