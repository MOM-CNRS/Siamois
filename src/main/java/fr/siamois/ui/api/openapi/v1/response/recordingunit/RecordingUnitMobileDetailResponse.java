package fr.siamois.ui.api.openapi.v1.response.recordingunit;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RecordingUnitMobileDetailResponse extends Response<RecordingUnitMobileDetailData> {

    public RecordingUnitMobileDetailResponse(RecordingUnitMobileDetailData data) {
        super(data);
    }
}
