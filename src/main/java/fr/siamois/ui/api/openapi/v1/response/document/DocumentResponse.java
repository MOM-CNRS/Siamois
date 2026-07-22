package fr.siamois.ui.api.openapi.v1.response.document;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class DocumentResponse extends Response<DocumentResource> {

    public DocumentResponse(DocumentResource data) {
        super(data);
    }
}
