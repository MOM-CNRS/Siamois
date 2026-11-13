package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectPatchRequest;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Bulk operations on projects: each element goes through the same service call as the unit
 * DELETE / PATCH /api/v1/projects/{id}.
 */
@Component
@RequiredArgsConstructor
public class ProjectBulkOperations implements BulkEntityOperations {

    private final ProjectApiService projectApiService;
    private final BulkPatchReader patchReader;

    /**
     * {@inheritDoc}
     */
    @Override
    public String collection() {
        return "projects";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(ProjectApiCaller caller, long id, Set<Long> selection, String lang) {
        projectApiService.deleteProject(caller, String.valueOf(id), lang);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void patch(ProjectApiCaller caller, long id, JsonNode patch, String lang) {
        projectApiService.patchProject(caller, String.valueOf(id), patchReader.read(patch, ProjectPatchRequest.class), lang);
    }
}
