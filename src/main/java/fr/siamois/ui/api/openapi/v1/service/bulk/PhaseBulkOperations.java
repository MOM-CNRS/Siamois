package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.ui.api.openapi.v1.request.phase.PhasePatchRequest;
import fr.siamois.ui.api.openapi.v1.service.PhaseOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Bulk operations on phases: each element goes through the same service call as the unit
 * DELETE / PATCH /api/v1/phases/{id}.
 */
@Component
@RequiredArgsConstructor
public class PhaseBulkOperations implements BulkEntityOperations, BulkDocumentLinkTarget {

    private final PhaseOpenApiService phaseOpenApiService;
    private final BulkPatchReader patchReader;

    /**
     * {@inheritDoc}
     */
    @Override
    public String collection() {
        return "phases";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(ProjectApiCaller caller, long id, Set<Long> selection, String lang) {
        phaseOpenApiService.deletePhase(id, caller.person(), caller.accessibleInstitutionIds(), lang);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void patch(ProjectApiCaller caller, long id, JsonNode patch, String lang) {
        phaseOpenApiService.applyPatchPhase(id, patchReader.read(patch, PhasePatchRequest.class),
                caller.person(), caller.accessibleInstitutionIds(), lang);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requireEditable(ProjectApiCaller caller, long id, String lang) {
        phaseOpenApiService.requireEditable(id, caller.person(), caller.accessibleInstitutionIds(),
                lang);
    }
}
