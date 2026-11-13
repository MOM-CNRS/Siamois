package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkDeleteRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkDocumentLinkRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkLinkMode;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkStratigraphyRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipRequest;
import org.mockito.ArgumentCaptor;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkFailureResource;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobStatus;
import fr.siamois.ui.api.openapi.v1.service.DocumentLinksOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkActionOpenApiServiceTest {

    private static final String LANG = "fr";
    private static final ProjectApiCaller CALLER = new ProjectApiCaller(person(7L), Set.of(1L), List.of());
    private static final ProjectApiCaller SOMEONE_ELSE = new ProjectApiCaller(person(8L), Set.of(1L), List.of());

    @Mock
    private DocumentLinksOpenApiService documentLinksOpenApiService;

    private BulkEntityOperations recordingUnits;
    private BulkDocumentLinkTarget finds;
    private BulkStratigraphyTarget stratigraphy;
    private BulkActionOpenApiService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * One collection with bulk operations, and the real failure translator.
     */
    @Mock
    private BulkJobStore store;
    private BulkJobRegistry registry;

    @BeforeEach
    void setUp() {
        // The store as a database would be: what a job wrote when it ended is what is read back.
        Map<String, BulkJobResource> ended = new HashMap<>();
        lenient().doAnswer(invocation -> {
            BulkJobResource snapshot = invocation.getArgument(0);
            ended.put(snapshot.id(), snapshot);
            return null;
        }).when(store).saveEnd(any(), any());
        lenient().when(store.find(anyString(), anyLong(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(ended.get(invocation.<String>getArgument(0)))
                        .filter(snapshot -> invocation.<Long>getArgument(1) == 7L));
        registry = new BulkJobRegistry(store);
        recordingUnits = mock(BulkEntityOperations.class);
        when(recordingUnits.collection()).thenReturn("recording-units");
        finds = mock(BulkDocumentLinkTarget.class);
        when(finds.collection()).thenReturn("finds");
        stratigraphy = mock(BulkStratigraphyTarget.class);
        when(stratigraphy.collection()).thenReturn("recording-units");
        // Called directly (no Spring proxy), the runner processes the job synchronously: the job is done on return.
        service = new BulkActionOpenApiService(List.of(recordingUnits), List.of(finds), List.of(stratigraphy),
                documentLinksOpenApiService,
                registry, new BulkAsyncRunner(new BulkFailureTranslator(), registry));
    }

    /**
     * A person with this id.
     */
    private static PersonDTO person(long id) {
        PersonDTO person = new PersonDTO();
        person.setId(id);
        return person;
    }

    /**
     * A delete request for these ids.
     */
    private static BulkDeleteRequest deleteRequest(Long... ids) {
        BulkDeleteRequest request = new BulkDeleteRequest();
        request.setIds(new ArrayList<>(Arrays.asList(ids)));
        return request;
    }

    /**
     * Deletes each element once, in order, each knowing the whole selection, and lists them all as deleted.
     */
    @Test
    void deleteActsOnEachDistinctIdWithTheSelection() {
        BulkJobResource result = service.delete("recording-units", deleteRequest(3L, 4L, 3L), CALLER, LANG);

        assertThat(result.succeeded()).containsExactly(3L, 4L);
        assertThat(result.failed()).isEmpty();
        verify(recordingUnits).delete(CALLER, 3L, Set.of(3L, 4L), LANG);
        verify(recordingUnits).delete(CALLER, 4L, Set.of(3L, 4L), LANG);
    }

    /**
     * The elements get the request's primary language, not its raw Accept-Language header.
     */
    @Test
    void deletePassesThePrimaryLanguage() {
        service.delete("recording-units", deleteRequest(3L), CALLER, "en-GB,en;q=0.9");

        verify(recordingUnits).delete(CALLER, 3L, Set.of(3L), "en");
    }

    /**
     * A refused element is reported with its status and message, and the others are still deleted.
     */
    @Test
    void deleteReportsAFailureWithoutStoppingTheOthers() {
        lenient().doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "L'UE a des relations"))
                .when(recordingUnits).delete(CALLER, 4L, Set.of(3L, 4L, 5L), LANG);

        BulkJobResource result = service.delete("recording-units", deleteRequest(3L, 4L, 5L), CALLER, LANG);

        assertThat(result.succeeded()).containsExactly(3L, 5L);
        assertThat(result.failed()).containsExactly(new BulkFailureResource(4L, 409, "L'UE a des relations"));
    }

    /**
     * An empty selection, or one too large, is refused as a whole.
     */
    @Test
    void deleteRefusesAnEmptyOrOversizedSelection() {
        BulkDeleteRequest empty = deleteRequest();
        BulkDeleteRequest tooMany = new BulkDeleteRequest();
        tooMany.setIds(LongStream.rangeClosed(1, BulkActionOpenApiService.MAX_IDS + 1L).boxed().toList());

        assertThatThrownBy(() -> service.delete("recording-units", empty, CALLER, LANG))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.delete("recording-units", tooMany, CALLER, LANG))
                .isInstanceOf(ResponseStatusException.class);
        verify(recordingUnits, never()).delete(any(), anyLong(), any(), any());
    }

    /**
     * A collection without bulk operations is unknown.
     */
    @Test
    void unknownCollectionIsNotFound() {
        BulkDeleteRequest request = deleteRequest(1L);

        assertThatThrownBy(() -> service.delete("organizations", request, CALLER, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * The same patch reaches every element.
     */
    @Test
    void patchAppliesTheSamePatchToEachElement() {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.putObject("answers").putObject("parents").putArray("add").add(42);
        BulkPatchRequest request = new BulkPatchRequest();
        request.setIds(List.of(3L, 4L));
        request.setPatch(patch);

        BulkJobResource result = service.patch("recording-units", request, CALLER, LANG);

        assertThat(result.succeeded()).containsExactly(3L, 4L);
        verify(recordingUnits).patch(CALLER, 3L, patch, LANG);
        verify(recordingUnits).patch(CALLER, 4L, patch, LANG);
    }

    /**
     * An identifier, unique per element, is never written in bulk; nor is an empty patch.
     */
    @Test
    void patchRefusesAnIdentifierOrNoPatch() {
        ObjectNode withIdentifier = objectMapper.createObjectNode().put("identifier", "UE-1");
        BulkPatchRequest renaming = new BulkPatchRequest();
        renaming.setIds(List.of(3L));
        renaming.setPatch(withIdentifier);
        BulkPatchRequest empty = new BulkPatchRequest();
        empty.setIds(List.of(3L));

        assertThatThrownBy(() -> service.patch("recording-units", renaming, CALLER, LANG))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.patch("recording-units", empty, CALLER, LANG))
                .isInstanceOf(ResponseStatusException.class);
        verify(recordingUnits, never()).patch(any(), anyLong(), any(JsonNode.class), any());
    }

    /**
     * LINK links the document to each element, UNLINK removes the link from each.
     */
    @Test
    void linkDocumentLinksOrUnlinksEachElement() {
        BulkDocumentLinkRequest request = new BulkDocumentLinkRequest();
        request.setIds(List.of(3L, 4L));
        request.setDocumentId(88L);
        request.setMode(BulkLinkMode.LINK);

        service.linkDocument("finds", request, CALLER, LANG);
        request.setMode(BulkLinkMode.UNLINK);
        service.linkDocument("finds", request, CALLER, LANG);

        verify(documentLinksOpenApiService).link(DocumentLinkKind.FIND, 3L, 88L, CALLER);
        verify(documentLinksOpenApiService).link(DocumentLinkKind.FIND, 4L, 88L, CALLER);
        verify(documentLinksOpenApiService).unlink(DocumentLinkKind.FIND, 3L, 88L, CALLER);
        verify(documentLinksOpenApiService).unlink(DocumentLinkKind.FIND, 4L, 88L, CALLER);
    }

    /**
     * A document link needs the document.
     */
    @Test
    void linkDocumentRequiresTheDocument() {
        BulkDocumentLinkRequest request = new BulkDocumentLinkRequest();
        request.setIds(List.of(3L));

        assertThatThrownBy(() -> service.linkDocument("finds", request, CALLER, LANG))
                .isInstanceOf(ResponseStatusException.class);
    }

    /**
     * An element the caller may not edit keeps its links: reported as forbidden, the others are linked.
     */
    @Test
    void linkDocumentSkipsTheElementsTheCallerMayNotEdit() {
        lenient().doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée"))
                .when(finds).requireEditable(CALLER, 4L, LANG);
        BulkDocumentLinkRequest request = new BulkDocumentLinkRequest();
        request.setIds(List.of(3L, 4L));
        request.setDocumentId(88L);
        request.setMode(BulkLinkMode.LINK);

        BulkJobResource result = service.linkDocument("finds", request, CALLER, LANG);

        assertThat(result.succeeded()).containsExactly(3L);
        assertThat(result.failed()).containsExactly(new BulkFailureResource(4L, 403, "Modification non autorisée"));
        verify(documentLinksOpenApiService).link(DocumentLinkKind.FIND, 3L, 88L, CALLER);
        verify(documentLinksOpenApiService, never()).link(DocumentLinkKind.FIND, 4L, 88L, CALLER);
    }

    /**
     * Documents can't be linked in bulk to a collection that isn't a document link target.
     */
    @Test
    void linkDocumentRefusesACollectionDocumentsDoNotLinkTo() {
        BulkDocumentLinkRequest request = new BulkDocumentLinkRequest();
        request.setIds(List.of(3L));
        request.setDocumentId(88L);

        assertThatThrownBy(() -> service.linkDocument("studies", request, CALLER, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * An element the caller may not edit is refused by its unit operation: reported, the others go on.
     */
    @Test
    void patchReportsAForbiddenElement() {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.putObject("answers").putObject("comment").put("value", "Remblai");
        BulkPatchRequest request = new BulkPatchRequest();
        request.setIds(List.of(3L, 4L));
        request.setPatch(patch);
        lenient().doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée"))
                .when(recordingUnits).patch(CALLER, 4L, patch, LANG);

        BulkJobResource result = service.patch("recording-units", request, CALLER, LANG);

        assertThat(result.succeeded()).containsExactly(3L);
        assertThat(result.failed()).containsExactly(new BulkFailureResource(4L, 403, "Modification non autorisée"));
    }

    /**
     * A job is followed by the one who started it: done, with its result, once every element is processed.
     */
    @Test
    void theCallerFollowsTheJobItStarted() {
        BulkJobResource started = service.delete("recording-units", deleteRequest(3L, 4L), CALLER, LANG);

        BulkJobResource followed = service.job(started.id(), CALLER.person());

        assertThat(followed.status()).isEqualTo(BulkJobStatus.DONE);
        assertThat(followed.total()).isEqualTo(2);
        assertThat(followed.processed()).isEqualTo(2);
        assertThat(followed.succeeded()).containsExactly(3L, 4L);
    }

    /**
     * Someone else's job, like an unknown one, is not found.
     */
    @Test
    void someoneElsesJobIsNotFound() {
        String jobId = service.delete("recording-units", deleteRequest(3L), CALLER, LANG).id();

        PersonDTO someoneElse = SOMEONE_ELSE.person();
        PersonDTO caller = CALLER.person();

        assertThatThrownBy(() -> service.job(jobId, someoneElse))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> service.job("unknown", caller))
                .isInstanceOf(ResponseStatusException.class);
    }

    /**
     * LINK adds the same relationship to each unit, towards the same other unit.
     */
    @Test
    void linkStratigraphyAddsTheSameRelationshipToEachUnit() {
        BulkStratigraphyRequest request = new BulkStratigraphyRequest();
        request.setIds(List.of(3L, 4L));
        request.setRelatedRecordingUnitId(42L);
        request.setPosition("anterior");
        request.setTypeConceptId(9L);

        BulkJobResource job = service.linkStratigraphy("recording-units", request, CALLER);

        ArgumentCaptor<RecordingUnitStratigraphicRelationshipRequest> sent =
                ArgumentCaptor.forClass(RecordingUnitStratigraphicRelationshipRequest.class);
        verify(stratigraphy).addStratigraphy(org.mockito.ArgumentMatchers.eq(CALLER), org.mockito.ArgumentMatchers.eq(3L), sent.capture());
        assertThat(sent.getValue().getRelatedRecordingUnitId()).isEqualTo(42L);
        assertThat(sent.getValue().getPosition()).isEqualTo("anterior");
        assertThat(sent.getValue().getTypeConceptId()).isEqualTo(9L);
        assertThat(job.succeeded()).containsExactly(3L, 4L);
    }

    /**
     * UNLINK removes the relationship with the other unit from each unit.
     */
    @Test
    void unlinkStratigraphyRemovesTheRelationshipFromEachUnit() {
        BulkStratigraphyRequest request = new BulkStratigraphyRequest();
        request.setIds(List.of(3L));
        request.setRelatedRecordingUnitId(42L);
        request.setMode(BulkLinkMode.UNLINK);

        service.linkStratigraphy("recording-units", request, CALLER);

        verify(stratigraphy).removeStratigraphy(CALLER, 3L, 42L);
    }

    /**
     * Adding needs a valid position; a collection without stratigraphy is unknown.
     */
    @Test
    void linkStratigraphyRefusesAMissingPositionOrAnotherCollection() {
        BulkStratigraphyRequest request = new BulkStratigraphyRequest();
        request.setIds(List.of(3L));
        request.setRelatedRecordingUnitId(42L);

        assertThatThrownBy(() -> service.linkStratigraphy("recording-units", request, CALLER))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.linkStratigraphy("finds", request, CALLER))
                .isInstanceOf(ResponseStatusException.class);
    }

    /**
     * A stop asked while a job runs reaches it; one asked for an unknown job is not found.
     */
    @Test
    void cancelAsksToStopTheJobOrIsNotFound() {
        when(store.requestCancel("job-1", 7L)).thenReturn(true);

        PersonDTO person = CALLER.person();

        service.cancel("job-1", person);

        assertThatThrownBy(() -> service.cancel("unknown", person))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
