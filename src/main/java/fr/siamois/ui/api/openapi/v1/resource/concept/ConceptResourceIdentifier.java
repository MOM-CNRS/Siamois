package fr.siamois.ui.api.openapi.v1.resource.concept;

import fr.siamois.ui.api.openapi.v1.generic.ResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConceptResourceIdentifier implements ResourceIdentifier {

    @Schema(description = "Resource type (always 'concept')",
            example = "concept",
            allowableValues = {"concept"})
    private final String type;
    private String id;
}
