package fr.siamois.ui.api.openapi.v1.response.document;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.document.ProjectDocumentResource;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class DocumentResourceResponse extends Response<ProjectDocumentResource> {

    public DocumentResourceResponse(ProjectDocumentResource data) {
        super(data);
    }
}
