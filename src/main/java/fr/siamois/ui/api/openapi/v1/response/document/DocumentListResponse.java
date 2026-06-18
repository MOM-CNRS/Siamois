package fr.siamois.ui.api.openapi.v1.response.document;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = false)
public class DocumentListResponse extends ListResponse<DocumentResource> {
    public DocumentListResponse(List<DocumentResource> data, ListMeta meta) {
        super(data, meta);
    }
}
