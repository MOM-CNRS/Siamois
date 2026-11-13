package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerPatchRequest;
import fr.siamois.ui.api.openapi.v1.service.ContainerOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Bulk operations on containers: each element goes through the same service call as the unit
 * DELETE / PATCH /api/v1/containers/{id}.
 */
@Component
@RequiredArgsConstructor
public class ContainerBulkOperations implements BulkEntityOperations, BulkDocumentLinkTarget {

    private final ContainerOpenApiService containerOpenApiService;
    private final BulkPatchReader patchReader;

    /**
     * {@inheritDoc}
     */
    @Override
    public String collection() {
        return "containers";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(ProjectApiCaller caller, long id, Set<Long> selection, String lang) {
        containerOpenApiService.deleteContainer(id, selection, caller.person(), caller.accessibleInstitutionIds(), lang);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void patch(ProjectApiCaller caller, long id, JsonNode patch, String lang) {
        containerOpenApiService.applyPatchContainer(id, patchReader.read(patch, ContainerPatchRequest.class),
                caller.person(), caller.accessibleInstitutionIds(), lang);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requireEditable(ProjectApiCaller caller, long id, String lang) {
        containerOpenApiService.requireEditable(id, caller.person(), caller.accessibleInstitutionIds(),
                lang);
    }
}
