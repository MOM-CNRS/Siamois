package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.ui.api.openapi.v1.request.study.StudyPatchRequest;
import fr.siamois.ui.api.openapi.v1.service.StudyOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Bulk operations on studies: each element goes through the same service call as the unit
 * DELETE / PATCH /api/v1/studies/{id}.
 */
@Component
@RequiredArgsConstructor
public class StudyBulkOperations implements BulkEntityOperations {

    private final StudyOpenApiService studyOpenApiService;
    private final BulkPatchReader patchReader;

    /**
     * {@inheritDoc}
     */
    @Override
    public String collection() {
        return "studies";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(ProjectApiCaller caller, long id, Set<Long> selection, String lang) {
        studyOpenApiService.deleteStudy(id, caller.person(), caller.accessibleInstitutionIds(), lang);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void patch(ProjectApiCaller caller, long id, JsonNode patch, String lang) {
        studyOpenApiService.applyPatchStudy(id, patchReader.read(patch, StudyPatchRequest.class),
                caller.person(), caller.accessibleInstitutionIds(),
                lang);
    }
}
