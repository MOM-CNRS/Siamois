package fr.siamois.ui.api.openapi.v1.resource.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.ResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DocumentResourceIdentifier implements ResourceIdentifier {

    @Schema(description = "Resource type",
            example = "documents",
            allowableValues = {"documents"})
    private String resourceType;

    private String id;
}
