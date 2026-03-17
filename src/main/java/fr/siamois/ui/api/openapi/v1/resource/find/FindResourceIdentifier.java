package fr.siamois.ui.api.openapi.v1.resource.find;

import io.swagger.v3.oas.annotations.media.Schema;

public class FindResourceIdentifier {

    @Schema(description = "Resource type",
            example = "finds",
            allowableValues = {"finds"})
    private String resourceType;

    private String id;

}
