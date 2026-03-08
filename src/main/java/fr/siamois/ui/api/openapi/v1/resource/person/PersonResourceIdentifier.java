package fr.siamois.ui.api.openapi.v1.resource.person;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.ResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PersonResourceIdentifier implements ResourceIdentifier {

    @Schema(description = "Resource type (always 'person')",
            example = "person",
            allowableValues = {"person"})
    @JsonProperty("_type")
    private final String resourceType;
    @JsonProperty("_id")
    private String resourceId;
}
