package fr.siamois.ui.api.openapi.v1.response;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConceptResponse extends Response<ConceptResource> {
    public ConceptResponse(ConceptResource data) {
        super(data);
    }
}
