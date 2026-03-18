package fr.siamois.ui.api.openapi.v1.response;

import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class RecordingUnitListResponse extends ListResponse<RecordingUnitResource> {
    public RecordingUnitListResponse(List<RecordingUnitResource> data, ListMeta meta) {
        super(data, meta);
    }
}
