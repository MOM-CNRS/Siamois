package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.find.FindPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.phase.PhasePatchRequest;
import fr.siamois.ui.api.openapi.v1.request.place.PlacePatchRequest;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipRequest;
import fr.siamois.ui.api.openapi.v1.request.study.StudyPatchRequest;
import fr.siamois.ui.api.openapi.v1.service.ContainerOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.DocumentContentOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.DocumentOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.FindOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.PhaseOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.PlaceOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitStratigraphyWriteService;
import fr.siamois.ui.api.openapi.v1.service.StudyOpenApiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Each collection's bulk operations hand each element to its unit service call, with the caller's
 * person, institutions and primary language.
 */
@ExtendWith(MockitoExtension.class)
class BulkOperationsTest {

    private static final String LANG = "fr";
    private static final String ACCEPT = "fr-FR,fr;q=0.9";
    private static final PersonDTO PERSON = new PersonDTO();
    private static final Set<Long> INSTITUTIONS = Set.of(1L);
    private static final ProjectApiCaller CALLER = new ProjectApiCaller(PERSON, INSTITUTIONS, List.of());

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BulkPatchReader patchReader = new BulkPatchReader(objectMapper);

    @Mock
    private ContainerOpenApiService containerOpenApiService;
    @Mock
    private DocumentOpenApiService documentOpenApiService;
    @Mock
    private DocumentContentOpenApiService documentContentOpenApiService;
    @Mock
    private FindOpenApiService findOpenApiService;
    @Mock
    private PhaseOpenApiService phaseOpenApiService;
    @Mock
    private PlaceOpenApiService placeOpenApiService;
    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private RecordingUnitOpenApiService recordingUnitOpenApiService;
    @Mock
    private RecordingUnitStratigraphyWriteService stratigraphyWriteService;
    @Mock
    private StudyOpenApiService studyOpenApiService;

    /**
     * A patch carrying only answers: a form patch every collection accepts.
     */
    private JsonNode answersPatch() {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.putObject("answers");
        patch.put("validationStatus", "VALIDATED");
        return patch;
    }

    /**
     * The recording unit bulk operations over the mocks.
     */
    private RecordingUnitBulkOperations recordingUnits() {
        return new RecordingUnitBulkOperations(projectApiService, recordingUnitOpenApiService, stratigraphyWriteService,
                patchReader);
    }

    /**
     * A patch body is read into the collection's request type.
     */
    @Test
    void patchReaderReadsTheRequestType() {
        PhasePatchRequest request = patchReader.read(answersPatch(), PhasePatchRequest.class);

        assertThat(request).isNotNull();
    }

    /**
     * A patch body that does not fit the request type is a bad request.
     */
    @Test
    void patchReaderRefusesAnUnreadablePatch() {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.put("answers", "not an object");

        assertThatThrownBy(() -> patchReader.read(patch, PhasePatchRequest.class))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    /**
     * Containers: deleted, patched, checked editable.
     */
    @Test
    void containerOperationsCallTheContainerService() {
        ContainerBulkOperations operations = new ContainerBulkOperations(containerOpenApiService, patchReader);

        operations.delete(CALLER, 5L, ACCEPT);
        operations.patch(CALLER, 5L, answersPatch(), ACCEPT);
        operations.requireEditable(CALLER, 5L, ACCEPT);

        assertThat(operations.collection()).isEqualTo("containers");
        verify(containerOpenApiService).deleteContainer(5L, PERSON, INSTITUTIONS, LANG);
        verify(containerOpenApiService).patchContainer(eq(5L), any(ContainerPatchRequest.class),
                eq(PERSON), eq(INSTITUTIONS), eq(LANG));
        verify(containerOpenApiService).requireEditable(5L, PERSON, INSTITUTIONS, LANG);
    }

    /**
     * Documents: deleted, and patched on their form only.
     */
    @Test
    void documentOperationsCallTheDocumentServices() {
        DocumentBulkOperations operations = new DocumentBulkOperations(documentOpenApiService,
                documentContentOpenApiService, patchReader);

        operations.delete(CALLER, 5L, ACCEPT);
        operations.patch(CALLER, 5L, answersPatch(), ACCEPT);

        assertThat(operations.collection()).isEqualTo("documents");
        verify(documentContentOpenApiService).deleteAccessibleDocument(5L, CALLER);
        verify(documentOpenApiService).patchDocument(eq(5L), any(DocumentPatchRequest.class),
                eq(PERSON), eq(INSTITUTIONS), eq(LANG));
    }

    /**
     * A document patch touching more than its form is refused, the document left as it is.
     */
    @Test
    void documentPatchRefusesMoreThanTheForm() {
        DocumentBulkOperations operations = new DocumentBulkOperations(documentOpenApiService,
                documentContentOpenApiService, patchReader);
        ObjectNode patch = objectMapper.createObjectNode();
        patch.put("title", "Nouveau titre");

        assertThatThrownBy(() -> operations.patch(CALLER, 5L, patch, LANG))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(documentOpenApiService);
    }

    /**
     * Finds: deleted, patched, checked editable.
     */
    @Test
    void findOperationsCallTheFindService() {
        FindBulkOperations operations = new FindBulkOperations(findOpenApiService, patchReader);

        operations.delete(CALLER, 5L, ACCEPT);
        operations.patch(CALLER, 5L, answersPatch(), ACCEPT);
        operations.requireEditable(CALLER, 5L, ACCEPT);

        assertThat(operations.collection()).isEqualTo("finds");
        verify(findOpenApiService).deleteFind(5L, PERSON, INSTITUTIONS, LANG);
        verify(findOpenApiService).patchFind(eq(5L), any(FindPatchRequest.class),
                eq(PERSON), eq(INSTITUTIONS), eq(LANG));
        verify(findOpenApiService).requireEditable(5L, PERSON, INSTITUTIONS, LANG);
    }

    /**
     * Phases: deleted, patched, checked editable.
     */
    @Test
    void phaseOperationsCallThePhaseService() {
        PhaseBulkOperations operations = new PhaseBulkOperations(phaseOpenApiService, patchReader);

        operations.delete(CALLER, 5L, ACCEPT);
        operations.patch(CALLER, 5L, answersPatch(), ACCEPT);
        operations.requireEditable(CALLER, 5L, ACCEPT);

        assertThat(operations.collection()).isEqualTo("phases");
        verify(phaseOpenApiService).deletePhase(5L, PERSON, INSTITUTIONS, LANG);
        verify(phaseOpenApiService).patchPhase(eq(5L), any(PhasePatchRequest.class), eq(PERSON), eq(INSTITUTIONS), eq(LANG));
        verify(phaseOpenApiService).requireEditable(5L, PERSON, INSTITUTIONS, LANG);
    }

    /**
     * Places: deleted, patched, checked editable.
     */
    @Test
    void placeOperationsCallThePlaceService() {
        PlaceBulkOperations operations = new PlaceBulkOperations(placeOpenApiService, patchReader);

        ObjectNode patch = objectMapper.createObjectNode();
        patch.put("name", "Site");

        operations.delete(CALLER, 5L, ACCEPT);
        operations.patch(CALLER, 5L, patch, ACCEPT);
        operations.requireEditable(CALLER, 5L, ACCEPT);

        assertThat(operations.collection()).isEqualTo("places");
        verify(placeOpenApiService).deletePlace(CALLER, 5L, LANG);
        verify(placeOpenApiService).updatePlace(eq(CALLER), eq(5L), any(PlacePatchRequest.class), eq(LANG));
        verify(placeOpenApiService).requireEditable(CALLER, 5L, LANG);
    }

    /**
     * Projects: deleted and patched.
     */
    @Test
    void projectOperationsCallTheProjectService() {
        ProjectBulkOperations operations = new ProjectBulkOperations(projectApiService, patchReader);

        operations.delete(CALLER, 5L, ACCEPT);
        operations.patch(CALLER, 5L, answersPatch(), ACCEPT);

        assertThat(operations.collection()).isEqualTo("projects");
        verify(projectApiService).deleteProject(CALLER, "5", LANG);
        verify(projectApiService).patchProject(eq(CALLER), eq("5"), any(ProjectPatchRequest.class), eq(LANG));
    }

    /**
     * Recording units: deleted, patched, checked editable, their stratigraphy added or removed.
     */
    @Test
    void recordingUnitOperationsCallTheRecordingUnitServices() {
        RecordingUnitBulkOperations operations = recordingUnits();
        RecordingUnitStratigraphicRelationshipRequest relationship = new RecordingUnitStratigraphicRelationshipRequest();

        operations.delete(CALLER, 5L, ACCEPT);
        operations.patch(CALLER, 5L, answersPatch(), ACCEPT);
        operations.requireEditable(CALLER, 5L, ACCEPT);
        operations.addStratigraphy(CALLER, 5L, relationship);
        operations.removeStratigraphy(CALLER, 5L, 8L);

        assertThat(operations.collection()).isEqualTo("recording-units");
        verify(projectApiService).deleteRecordingUnit(CALLER, 5L, ACCEPT);
        verify(recordingUnitOpenApiService).patchRecordingUnit(eq("5"), any(RecordingUnitPatchRequest.class),
                eq(PERSON), eq(INSTITUTIONS), eq(LANG));
        verify(recordingUnitOpenApiService).requireEditable(5L, PERSON, INSTITUTIONS, LANG);
        verify(stratigraphyWriteService).add("5", relationship, PERSON, INSTITUTIONS);
        verify(stratigraphyWriteService).remove("5", 8L, PERSON, INSTITUTIONS);
    }

    /**
     * Studies: deleted and patched.
     */
    @Test
    void studyOperationsCallTheStudyService() {
        StudyBulkOperations operations = new StudyBulkOperations(studyOpenApiService, patchReader);

        operations.delete(CALLER, 5L, ACCEPT);
        operations.patch(CALLER, 5L, answersPatch(), ACCEPT);

        assertThat(operations.collection()).isEqualTo("studies");
        verify(studyOpenApiService).deleteStudy(5L, PERSON, INSTITUTIONS, LANG);
        verify(studyOpenApiService).patchStudy(eq(5L), any(StudyPatchRequest.class),
                eq(PERSON), eq(INSTITUTIONS), eq(LANG));
    }
}
