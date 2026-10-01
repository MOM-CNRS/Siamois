package fr.siamois.ui.api.openapi.v1.response.phase;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class PhaseResponse extends Response<PhaseResource> {
    public PhaseResponse(PhaseResource data) {
        super(data);
    }
}
