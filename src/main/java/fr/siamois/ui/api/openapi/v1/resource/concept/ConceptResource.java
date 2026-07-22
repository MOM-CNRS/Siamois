package fr.siamois.ui.api.openapi.v1.resource.concept;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConceptResource extends ConceptResourceIdentifier {

        @Schema(description = "Label of the concept in the accepted language")
        private String prefLabel; // label resolved in the language requested
}
