package fr.siamois.ui.api.openapi.v1.resource.concept;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResolvedConceptResource extends ConceptResourceIdentifier {

        @Schema(description = "Label, resolved in the language requested")
        private String resolvedLabel; // label of the concept, in the choosen language
        @Schema(description = "External URL of the concept")
        private String externalUrl;
}
