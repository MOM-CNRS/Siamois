package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipRequest;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitStratigraphyWriteService;
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
public class RecordingUnitBulkOperations implements BulkEntityOperations, BulkDocumentLinkTarget, BulkStratigraphyTarget {

    private final ProjectApiService projectApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    private final RecordingUnitStratigraphyWriteService stratigraphyWriteService;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void requireEditable(ProjectApiCaller caller, long id, String acceptLanguage) {
        recordingUnitOpenApiService.requireEditable(id, caller.person(), caller.accessibleInstitutionIds(),
                ProjectApiService.primaryAcceptLanguage(acceptLanguage));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addStratigraphy(ProjectApiCaller caller, long id, RecordingUnitStratigraphicRelationshipRequest relationship) {
        stratigraphyWriteService.add(String.valueOf(id), relationship, caller.person(), caller.accessibleInstitutionIds());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeStratigraphy(ProjectApiCaller caller, long id, long relatedId) {
        stratigraphyWriteService.remove(String.valueOf(id), relatedId, caller.person(), caller.accessibleInstitutionIds());
    }
}
