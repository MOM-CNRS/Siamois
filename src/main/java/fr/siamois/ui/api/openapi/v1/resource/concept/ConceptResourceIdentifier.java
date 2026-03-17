package fr.siamois.ui.api.openapi.v1.resource.concept;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.ResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConceptResourceIdentifier implements ResourceIdentifier {

    @Schema(description = "Resource type (always 'concepts')",
            example = "concepts",
            allowableValues = {"concepts"})
    private String resourceType;
    @JsonProperty("resourceId")
    private String id;

}
