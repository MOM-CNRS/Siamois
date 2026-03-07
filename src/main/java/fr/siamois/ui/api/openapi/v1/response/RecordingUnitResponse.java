package fr.siamois.ui.api.openapi.v1.response;

import fr.siamois.ui.api.openapi.v1.generic.Response;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RecordingUnitResponse extends Response<RecordingUnitResource> {
    public RecordingUnitResponse(RecordingUnitResource data) {
        super(data);
    }
}
