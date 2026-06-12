package fr.siamois.ui.api.openapi.v1.response.sync;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class SyncConflictResponse extends Response<SyncConflictData> {

    public SyncConflictResponse(SyncConflictData data) {
        super(data);
    }
}
