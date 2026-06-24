package fr.siamois.ui.api.openapi.v1.response.document;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
public class DocumentListResponse extends ListResponse<DocumentResource> {


    public DocumentListResponse(List<DocumentResource> documents, ListMeta meta) {
        super(documents, meta);
    }
}
