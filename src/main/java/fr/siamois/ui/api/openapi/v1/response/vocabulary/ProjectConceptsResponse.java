package fr.siamois.ui.api.openapi.v1.response.vocabulary;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ProjectConceptsResponse extends Response<List<ResolvedConceptResource>> {

    public ProjectConceptsResponse(List<ResolvedConceptResource> data) {
        super(data);
    }
}
