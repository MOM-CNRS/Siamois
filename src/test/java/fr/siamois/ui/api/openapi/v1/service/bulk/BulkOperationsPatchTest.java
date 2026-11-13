package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.request.phase.PhasePatchRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitPatchRequest;
import fr.siamois.ui.api.openapi.v1.service.PhaseOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitStratigraphyWriteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BulkOperationsPatchTest {

    private static final ProjectApiCaller CALLER = new ProjectApiCaller(new PersonDTO(), Set.of(1L), List.of());

    @Mock
    private PhaseOpenApiService phaseOpenApiService;
    @Mock
    private RecordingUnitOpenApiService recordingUnitOpenApiService;
    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private RecordingUnitStratigraphyWriteService stratigraphyWriteService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BulkPatchReader patchReader = new BulkPatchReader(objectMapper);

    /**
     * A bulk patch of a phase writes it without building its resource, which nobody reads.
     */
    @Test
    void aPhaseIsPatchedWithoutBuildingItsResource() {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.putObject("answers");

        new PhaseBulkOperations(phaseOpenApiService, patchReader).patch(CALLER, 5L, patch, "fr");

        verify(phaseOpenApiService).applyPatchPhase(org.mockito.ArgumentMatchers.eq(5L), any(PhasePatchRequest.class),
                any(), any(), any());
        verify(phaseOpenApiService, never()).patchPhase(anyLong(), any(), any(), any(), any());
    }

    /**
     * A bulk patch of a recording unit writes it without building its detail, which nobody reads.
     */
    @Test
    void aRecordingUnitIsPatchedWithoutBuildingItsDetail() {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.putObject("answers");

        new RecordingUnitBulkOperations(projectApiService, recordingUnitOpenApiService, stratigraphyWriteService, patchReader)
                .patch(CALLER, 5L, patch, "fr");

        verify(recordingUnitOpenApiService).applyPatchRecordingUnit(org.mockito.ArgumentMatchers.eq("5"),
                any(RecordingUnitPatchRequest.class), any(), any(), any());
        verify(recordingUnitOpenApiService, never()).patchRecordingUnit(anyString(), any(), any(), any(), any());
    }
}
