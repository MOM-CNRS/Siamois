package fr.siamois.ui.api.openapi.v1.resource.concept;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.ResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConceptResource extends ConceptResourceIdentifier {
        private String prefLabel; // label of the concept, in the choosen language
}
