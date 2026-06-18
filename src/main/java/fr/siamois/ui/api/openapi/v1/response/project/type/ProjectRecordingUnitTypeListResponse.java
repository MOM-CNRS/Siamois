package fr.siamois.ui.api.openapi.v1.response.project.type;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectRecordingUnitTypeListResponse extends ListResponse<RecordingUnitType> {
    public ProjectRecordingUnitTypeListResponse(List<RecordingUnitType> data, ListMeta meta) {
        super(data, meta);
    }
}
