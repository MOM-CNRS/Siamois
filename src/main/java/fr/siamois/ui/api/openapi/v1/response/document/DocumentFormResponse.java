package fr.siamois.ui.api.openapi.v1.response.document;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class DocumentFormResponse extends Response<DocumentFormData> {

    public DocumentFormResponse(DocumentFormData data) {
        super(data);
    }
}
