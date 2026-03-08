package fr.siamois.ui.api.openapi.v1.resource.concept;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.ResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConceptResourceIdentifier implements ResourceIdentifier {

    @Schema(description = "Resource type (always 'concept')",
            example = "concept",
            allowableValues = {"concept"})
    @JsonProperty("_type")
    private String resourceType;
    @JsonProperty("_id")
    private String resourceId;

}
