package fr.siamois.ui.api.openapi.v1.resource.person;

import fr.siamois.ui.api.openapi.v1.generic.ResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PersonResourceIdentifier implements ResourceIdentifier {

    @Schema(description = "Resource type (always 'person')",
            example = "person",
            allowableValues = {"person"})
    private final String type;
    private String id;
}
