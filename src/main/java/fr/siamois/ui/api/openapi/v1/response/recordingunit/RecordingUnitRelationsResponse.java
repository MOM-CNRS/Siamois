package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RecordingUnitRelationsResponse extends Response<RecordingUnitRelationsData> {

    public RecordingUnitRelationsResponse(RecordingUnitRelationsData data) {
        super(data);
    }
}
