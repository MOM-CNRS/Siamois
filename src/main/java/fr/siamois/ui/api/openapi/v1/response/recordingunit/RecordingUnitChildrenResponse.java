package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitChildrenData;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RecordingUnitChildrenResponse extends Response<RecordingUnitChildrenData> {

    public RecordingUnitChildrenResponse(RecordingUnitChildrenData data) {
        super(data);
    }
}
