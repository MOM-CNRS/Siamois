package fr.siamois.ui.api.openapi.v1.resource.concept;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConceptResource extends ConceptResourceIdentifier {
        private String prefLabel; // label of the concept, in the choosen language
}
