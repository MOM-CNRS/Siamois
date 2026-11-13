package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.domain.services.document.DocumentLinkService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipRequest;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitStratigraphyWriteService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Bulk operations on recording units: each element goes through the same service call as the unit
 * DELETE / PATCH /api/v1/recording-units/{id}.
 */
@Component
@RequiredArgsConstructor
public class RecordingUnitBulkOperations implements BulkEntityOperations, BulkDocumentLinkTarget, BulkStratigraphyTarget, BulkDuplicateTarget {

    private final ProjectApiService projectApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    private final RecordingUnitStratigraphyWriteService stratigraphyWriteService;
    private final RecordingUnitService recordingUnitService;
    private final DocumentLinkService documentLinkService;
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
    public void delete(ProjectApiCaller caller, long id, Set<Long> selection, String lang) {
        projectApiService.deleteRecordingUnit(caller, id, selection, lang);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void patch(ProjectApiCaller caller, long id, JsonNode patch, String lang) {
        recordingUnitOpenApiService.applyPatchRecordingUnit(String.valueOf(id), patchReader.read(patch, RecordingUnitPatchRequest.class),
                caller.person(), caller.accessibleInstitutionIds(), lang);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requireEditable(ProjectApiCaller caller, long id, String lang) {
        recordingUnitOpenApiService.requireEditable(id, caller.person(), caller.accessibleInstitutionIds(),
                lang);
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

    /**
     * {@inheritDoc}
     * <p>
     * The copy keeps its source's parents, phases, stratigraphic relationships and documents, in the same
     * transaction: a relation that can't be copied undoes the copy.
     */
    @Override
    @Transactional
    public void duplicate(ProjectApiCaller caller, long id, String lang) {
        long copyId = Long.parseLong(recordingUnitOpenApiService
                .duplicateRecordingUnit(String.valueOf(id), caller.person(), caller.accessibleInstitutionIds(), lang)
                .getId());
        recordingUnitService.copyRelations(id, copyId);
        documentLinkService.copyLinks(DocumentLinkKind.RECORDING_UNIT, id, copyId);
    }
}
