package fr.siamois.ui.api.openapi.v1.response;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConceptListResponse extends ListResponse<ConceptResource> {
    public ConceptListResponse(List<ConceptResource> data, ListMeta meta) {
        super(data, meta);
    }
}
