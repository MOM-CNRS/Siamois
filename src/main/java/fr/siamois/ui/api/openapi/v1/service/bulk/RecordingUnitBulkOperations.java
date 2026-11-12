package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitPatchRequest;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bulk operations on recording units: each element goes through the same service call as the unit
 * DELETE / PATCH /api/v1/recording-units/{id}.
 */
@Component
@RequiredArgsConstructor
public class RecordingUnitBulkOperations implements BulkEntityOperations {

    private final ProjectApiService projectApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    private final BulkPatchReader patchReader;

    /**
     * {@inheritDoc}
     */
    @Override
    public String collection() {
        return "recording-units";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(ProjectApiCaller caller, long id, String acceptLanguage) {
        projectApiService.deleteRecordingUnit(caller, id, acceptLanguage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void patch(ProjectApiCaller caller, long id, JsonNode patch, String acceptLanguage) {
        recordingUnitOpenApiService.patchRecordingUnit(String.valueOf(id), patchReader.read(patch, RecordingUnitPatchRequest.class),
                caller.person(), caller.accessibleInstitutionIds(), ProjectApiService.primaryAcceptLanguage(acceptLanguage));
    }
}
