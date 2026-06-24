package fr.siamois.ui.api.openapi.v1.response.document;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
public class DocumentListResponse extends Response<DocumentListResponse.DocumentsData> {

    @Data
    public static class DocumentsData {
        private final List<DocumentResource> documents;
    }

    public DocumentListResponse(List<DocumentResource> documents, Object meta) {
        super(new DocumentsData(documents));
    }
}
