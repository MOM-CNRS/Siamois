package fr.siamois.ui.api.openapi.v1.response.phase;

import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PhaseListResponse extends ListResponse<PhaseResource> {
    public PhaseListResponse(List<PhaseResource> data, ListMeta meta) {
        super(data, meta);
    }
}
