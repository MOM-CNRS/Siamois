package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.domain.services.document.DocumentLinkService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.find.FindPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.phase.PhasePatchRequest;
import fr.siamois.ui.api.openapi.v1.request.place.PlacePatchRequest;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipRequest;
import fr.siamois.ui.api.openapi.v1.request.study.StudyPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
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
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Each collection's bulk operations hand each element to its unit service call, with the caller's
 * person, institutions and language.
 */
@ExtendWith(MockitoExtension.class)
class BulkOperationsTest {

    private static final String LANG = "fr";
    private static final PersonDTO PERSON = new PersonDTO();
    private static final Set<Long> INSTITUTIONS = Set.of(1L);
    private static final ProjectApiCaller CALLER = new ProjectApiCaller(PERSON, INSTITUTIONS, List.of());
    private static final Set<Long> SELECTION = Set.of(5L, 6L);

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
    private RecordingUnitService recordingUnitService;
    @Mock
    private SpecimenService specimenService;
    @Mock
    private DocumentLinkService documentLinkService;
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
                recordingUnitService, documentLinkService, patchReader);
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
     * Containers: deleted with the selection, patched, checked editable.
     */
    @Test
    void containerOperationsCallTheContainerService() {
        ContainerBulkOperations operations = new ContainerBulkOperations(containerOpenApiService, patchReader);

        operations.delete(CALLER, 5L, SELECTION, LANG);
        operations.patch(CALLER, 5L, answersPatch(), LANG);
        operations.requireEditable(CALLER, 5L, LANG);

        assertThat(operations.collection()).isEqualTo("containers");
        verify(containerOpenApiService).deleteContainer(5L, SELECTION, PERSON, INSTITUTIONS, LANG);
        verify(containerOpenApiService).applyPatchContainer(eq(5L), any(ContainerPatchRequest.class),
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

        operations.delete(CALLER, 5L, SELECTION, LANG);
        operations.patch(CALLER, 5L, answersPatch(), LANG);

        assertThat(operations.collection()).isEqualTo("documents");
        verify(documentContentOpenApiService).deleteAccessibleDocument(5L, CALLER);
        verify(documentOpenApiService).applyPatchDocument(eq(5L), any(DocumentPatchRequest.class),
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
     * Finds: deleted with the selection, patched, checked editable.
     */
    @Test
    void findOperationsCallTheFindService() {
        FindBulkOperations operations = new FindBulkOperations(findOpenApiService, specimenService,
                documentLinkService, patchReader);

        operations.delete(CALLER, 5L, SELECTION, LANG);
        operations.patch(CALLER, 5L, answersPatch(), LANG);
        operations.requireEditable(CALLER, 5L, LANG);

        assertThat(operations.collection()).isEqualTo("finds");
        verify(findOpenApiService).deleteFind(5L, SELECTION, PERSON, INSTITUTIONS, LANG);
        verify(findOpenApiService).applyPatchFind(eq(5L), any(FindPatchRequest.class),
                eq(PERSON), eq(INSTITUTIONS), eq(LANG));
        verify(findOpenApiService).requireEditable(5L, PERSON, INSTITUTIONS, LANG);
    }

    /**
     * A duplicated find keeps its source's relations and documents.
     */
    @Test
    void findDuplicateCopiesTheRelations() {
        FindResource copy = new FindResource();
        copy.setId("9");
        when(findOpenApiService.duplicateFind(5L, PERSON, INSTITUTIONS, LANG)).thenReturn(copy);

        new FindBulkOperations(findOpenApiService, specimenService, documentLinkService, patchReader)
                .duplicate(CALLER, 5L, LANG);

        InOrder order = inOrder(findOpenApiService, specimenService, documentLinkService);
        order.verify(findOpenApiService).duplicateFind(5L, PERSON, INSTITUTIONS, LANG);
        order.verify(specimenService).copyRelations(5L, 9L);
        order.verify(documentLinkService).copyLinks(DocumentLinkKind.FIND, 5L, 9L);
    }

    /**
     * Phases: deleted, patched, checked editable.
     */
    @Test
    void phaseOperationsCallThePhaseService() {
        PhaseBulkOperations operations = new PhaseBulkOperations(phaseOpenApiService, patchReader);

        operations.delete(CALLER, 5L, SELECTION, LANG);
        operations.requireEditable(CALLER, 5L, LANG);

        assertThat(operations.collection()).isEqualTo("phases");
        verify(phaseOpenApiService).deletePhase(5L, PERSON, INSTITUTIONS, LANG);
        verify(phaseOpenApiService).requireEditable(5L, PERSON, INSTITUTIONS, LANG);
    }

    /**
     * Places: deleted with the selection, patched, checked editable.
     */
    @Test
    void placeOperationsCallThePlaceService() {
        PlaceBulkOperations operations = new PlaceBulkOperations(placeOpenApiService, documentLinkService, patchReader);

        ObjectNode patch = objectMapper.createObjectNode();
        patch.put("name", "Site");

        operations.delete(CALLER, 5L, SELECTION, LANG);
        operations.patch(CALLER, 5L, patch, LANG);
        operations.requireEditable(CALLER, 5L, LANG);

        assertThat(operations.collection()).isEqualTo("places");
        verify(placeOpenApiService).deletePlace(CALLER, 5L, SELECTION, LANG);
        verify(placeOpenApiService).updatePlace(eq(CALLER), eq(5L), any(PlacePatchRequest.class), eq(LANG));
        verify(placeOpenApiService).requireEditable(CALLER, 5L, LANG);
    }

    /**
     * A duplicated place keeps its source's documents (its parents are kept by the unit duplication).
     */
    @Test
    void placeDuplicateCopiesTheDocuments() {
        when(placeOpenApiService.duplicatePlace(CALLER, 5L, LANG))
                .thenReturn(new PlaceCreatedResponse.PlaceCreatedItem(9L, "Site (1)", null));

        new PlaceBulkOperations(placeOpenApiService, documentLinkService, patchReader).duplicate(CALLER, 5L, LANG);

        verify(documentLinkService).copyLinks(DocumentLinkKind.PLACE, 5L, 9L);
    }

    /**
     * Projects: deleted, patched, duplicated with their places.
     */
    @Test
    void projectOperationsCallTheProjectService() {
        ProjectBulkOperations operations = new ProjectBulkOperations(projectApiService, patchReader);

        operations.delete(CALLER, 5L, SELECTION, LANG);
        operations.patch(CALLER, 5L, answersPatch(), LANG);
        operations.duplicate(CALLER, 5L, LANG);

        assertThat(operations.collection()).isEqualTo("projects");
        verify(projectApiService).deleteProject(CALLER, "5", LANG);
        verify(projectApiService).patchProject(eq(CALLER), eq("5"), any(ProjectPatchRequest.class), eq(LANG));
        verify(projectApiService).duplicateProjectWithPlaces(CALLER, "5", LANG);
        verify(projectApiService, never()).duplicateProject(any(), any(), any());
    }

    /**
     * Recording units: deleted with the selection, checked editable, their stratigraphy added or removed.
     */
    @Test
    void recordingUnitOperationsCallTheRecordingUnitServices() {
        RecordingUnitBulkOperations operations = recordingUnits();
        RecordingUnitStratigraphicRelationshipRequest relationship = new RecordingUnitStratigraphicRelationshipRequest();

        operations.delete(CALLER, 5L, SELECTION, LANG);
        operations.requireEditable(CALLER, 5L, LANG);
        operations.addStratigraphy(CALLER, 5L, relationship);
        operations.removeStratigraphy(CALLER, 5L, 8L);

        assertThat(operations.collection()).isEqualTo("recording-units");
        verify(projectApiService).deleteRecordingUnit(CALLER, 5L, SELECTION, LANG);
        verify(recordingUnitOpenApiService).requireEditable(5L, PERSON, INSTITUTIONS, LANG);
        verify(stratigraphyWriteService).add("5", relationship, PERSON, INSTITUTIONS);
        verify(stratigraphyWriteService).remove("5", 8L, PERSON, INSTITUTIONS);
    }

    /**
     * A duplicated recording unit keeps its source's relations and documents.
     */
    @Test
    void recordingUnitDuplicateCopiesTheRelations() {
        RecordingUnitResource copy = new RecordingUnitResource();
        copy.setId("9");
        when(recordingUnitOpenApiService.duplicateRecordingUnit("5", PERSON, INSTITUTIONS, LANG)).thenReturn(copy);

        recordingUnits().duplicate(CALLER, 5L, LANG);

        InOrder order = inOrder(recordingUnitOpenApiService, recordingUnitService, documentLinkService);
        order.verify(recordingUnitOpenApiService).duplicateRecordingUnit("5", PERSON, INSTITUTIONS, LANG);
        order.verify(recordingUnitService).copyRelations(5L, 9L);
        order.verify(documentLinkService).copyLinks(DocumentLinkKind.RECORDING_UNIT, 5L, 9L);
    }

    /**
     * Studies: deleted and patched.
     */
    @Test
    void studyOperationsCallTheStudyService() {
        StudyBulkOperations operations = new StudyBulkOperations(studyOpenApiService, patchReader);

        operations.delete(CALLER, 5L, SELECTION, LANG);
        operations.patch(CALLER, 5L, answersPatch(), LANG);

        assertThat(operations.collection()).isEqualTo("studies");
        verify(studyOpenApiService).deleteStudy(5L, PERSON, INSTITUTIONS, LANG);
        verify(studyOpenApiService).applyPatchStudy(eq(5L), any(StudyPatchRequest.class),
                eq(PERSON), eq(INSTITUTIONS), eq(LANG));
    }
}
