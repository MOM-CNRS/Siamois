package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkDeleteRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkDocumentLinkRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkDuplicateRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkLinkMode;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkStratigraphyRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipRequest;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobResource;
import fr.siamois.ui.api.openapi.v1.service.DocumentLinksOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitStratigraphyWriteService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

/**
 * The bulk actions on a list's selection: delete, patch (a field, a relation added or removed) and
 * link or unlink a document, on many elements of one collection in one request.
 *
 * <p>The request is checked at once (collection, ids, patch, document: 400/404 before anything starts);
 * the elements are then processed in the background (BulkAsyncRunner) and the caller gets a job to
 * poll for progress and, once done, the result.</p>
 *
 * <p>Deliberately NOT transactional: each element goes through its collection's unit operation, in
 * that operation's own transaction, with its own rights and rules. An element that is refused (a
 * relation still holding it, no right to edit it…) is reported in the result and leaves the others
 * untouched, which is what a user acting on a selection expects.</p>
 */
@Service
public class BulkActionOpenApiService {

    /** The most elements one bulk request may act on. */
    public static final int MAX_IDS = 10_000;

    private static final String IDENTIFIER_PROPERTY = "identifier";
    private static final Set<String> STRATIGRAPHIC_POSITIONS = Set.of(RecordingUnitStratigraphyWriteService.ANTERIOR,
            RecordingUnitStratigraphyWriteService.POSTERIOR, RecordingUnitStratigraphyWriteService.SYNCHRONOUS);

    private final Map<String, BulkEntityOperations> operationsByCollection;
    private final Map<String, BulkDocumentLinkTarget> documentTargetsByCollection;
    private final Map<String, BulkStratigraphyTarget> stratigraphyTargetsByCollection;
    private final Map<String, BulkDuplicateTarget> duplicateTargetsByCollection;
    private final DocumentLinksOpenApiService documentLinksOpenApiService;
    private final BulkJobRegistry jobRegistry;
    private final BulkAsyncRunner runner;

    /**
     * Indexes every collection's operations by its path segment.
     *
     * @param operations                  one per collection that supports bulk actions
     * @param documentTargets             one per collection documents can be linked to in bulk
     * @param stratigraphyTargets         one per collection whose elements take stratigraphic relationships in bulk
     * @param duplicateTargets            one per collection whose elements can be duplicated in bulk
     * @param documentLinksOpenApiService the document links' unit service
     * @param jobRegistry                 the jobs running or recently ended
     * @param runner                      what processes a job's elements in the background
     */
    public BulkActionOpenApiService(List<BulkEntityOperations> operations,
                                    List<BulkDocumentLinkTarget> documentTargets,
                                    List<BulkStratigraphyTarget> stratigraphyTargets,
                                    List<BulkDuplicateTarget> duplicateTargets,
                                    DocumentLinksOpenApiService documentLinksOpenApiService,
                                    BulkJobRegistry jobRegistry,
                                    BulkAsyncRunner runner) {
        this.operationsByCollection = byCollection(operations);
        this.documentTargetsByCollection = byCollection(documentTargets);
        this.stratigraphyTargetsByCollection = byCollection(stratigraphyTargets);
        this.duplicateTargetsByCollection = byCollection(duplicateTargets);
        this.documentLinksOpenApiService = documentLinksOpenApiService;
        this.jobRegistry = jobRegistry;
        this.runner = runner;
    }

    /**
     * Deletes each element, as its unit DELETE would — a link between two elements of the selection
     * does not stop their deletion: it goes with them.
     *
     * @param collection     the collection's path segment
     * @param request        the elements to delete
     * @param caller         the authenticated caller
     * @param acceptLanguage the request's Accept-Language header
     * @return the job deleting them, to poll
     */
    public BulkJobResource delete(String collection, BulkDeleteRequest request, ProjectApiCaller caller,
                                     String acceptLanguage) {
        BulkEntityOperations operations = targetOf(operationsByCollection, collection);
        List<Long> ids = idsOf(request.getIds());
        Set<Long> selection = Set.copyOf(ids);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return startJob(caller, ids, id -> operations.delete(caller, id, selection, lang));
    }

    /**
     * Duplicates each element, as its unit duplication would: the copy is the element alone — its
     * hierarchy (parents, children) and its relationships are not duplicated.
     *
     * @param collection     the collection's path segment
     * @param request        the elements to duplicate
     * @param caller         the authenticated caller
     * @param acceptLanguage the request's Accept-Language header
     * @return the job duplicating them, to poll
     */
    public BulkJobResource duplicate(String collection, BulkDuplicateRequest request, ProjectApiCaller caller,
                                     String acceptLanguage) {
        BulkDuplicateTarget target = targetOf(duplicateTargetsByCollection, collection);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return startJob(caller, idsOf(request.getIds()), id -> target.duplicate(caller, id, lang));
    }

    /**
     * Applies the same partial update to each element, as its unit PATCH would.
     *
     * @param collection     the collection's path segment
     * @param request        the elements and the update
     * @param caller         the authenticated caller
     * @param acceptLanguage the request's Accept-Language header
     * @return the job updating them, to poll
     */
    public BulkJobResource patch(String collection, BulkPatchRequest request, ProjectApiCaller caller,
                                    String acceptLanguage) {
        BulkEntityOperations operations = targetOf(operationsByCollection, collection);
        JsonNode patch = requireBulkPatch(request.getPatch());
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return startJob(caller, idsOf(request.getIds()), id -> operations.patch(caller, id, patch, lang));
    }

    /**
     * Links a document to each element, or removes that link from each — only on the elements the
     * caller may edit (checked element by element before the link changes).
     *
     * @param segment        the elements' collection path segment
     * @param request        the elements, the document and what to do
     * @param caller         the authenticated caller
     * @param acceptLanguage the request's Accept-Language header
     * @return the job changing their links, to poll
     */
    public BulkJobResource linkDocument(String segment, BulkDocumentLinkRequest request, ProjectApiCaller caller,
                                           String acceptLanguage) {
        BulkDocumentLinkTarget target = targetOf(documentTargetsByCollection, segment);
        DocumentLinkKind kind = DocumentLinkKind.ofPathSegment(segment)
                .orElseThrow(() -> unknownCollection(segment));
        long documentId = requireId(request.getDocumentId(), "documentId");
        LongConsumer link = documentLinkAction(modeOf(request.getMode()), kind, documentId, caller);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return startJob(caller, idsOf(request.getIds()), id -> {
            target.requireEditable(caller, id, lang);
            link.accept(id);
        });
    }

    /**
     * Adds to each element a stratigraphic relationship with the same other unit, or removes that
     * relationship from each — through the same service as the unit relationship endpoints.
     *
     * @param segment the elements' collection path segment
     * @param request the elements, the other unit, the relationship and what to do
     * @param caller  the authenticated caller
     * @return the job changing their relationships, to poll
     */
    public BulkJobResource linkStratigraphy(String segment, BulkStratigraphyRequest request, ProjectApiCaller caller) {
        BulkStratigraphyTarget target = targetOf(stratigraphyTargetsByCollection, segment);
        long relatedId = requireId(request.getRelatedRecordingUnitId(), "relatedRecordingUnitId");
        LongConsumer action = switch (modeOf(request.getMode())) {
            case LINK -> {
                RecordingUnitStratigraphicRelationshipRequest relationship = relationshipOf(request, relatedId);
                yield id -> target.addStratigraphy(caller, id, relationship);
            }
            case UNLINK -> id -> target.removeStratigraphy(caller, id, relatedId);
        };
        return startJob(caller, idsOf(request.getIds()), action);
    }

    /**
     * The relationship each element gets: the same position, type and certainty towards the same unit.
     */
    private static RecordingUnitStratigraphicRelationshipRequest relationshipOf(BulkStratigraphyRequest request, long relatedId) {
        if (request.getPosition() == null || !STRATIGRAPHIC_POSITIONS.contains(request.getPosition())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "position doit valoir anterior, posterior ou synchronous");
        }
        RecordingUnitStratigraphicRelationshipRequest relationship = new RecordingUnitStratigraphicRelationshipRequest();
        relationship.setRelatedRecordingUnitId(relatedId);
        relationship.setPosition(request.getPosition());
        relationship.setTypeConceptId(request.getTypeConceptId());
        relationship.setUncertain(Boolean.TRUE.equals(request.getUncertain()));
        return relationship;
    }

    /**
     * What to do to the links: link, unless told otherwise.
     */
    private static BulkLinkMode modeOf(BulkLinkMode requested) {
        return Objects.requireNonNullElse(requested, BulkLinkMode.LINK);
    }

    /**
     * A mandatory id of the request (400 when absent).
     */
    private static long requireId(Long id, String name) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " est obligatoire");
        }
        return id;
    }

    /**
     * What a document link mode does to one element (an exhaustive switch: a new mode must say what it does).
     */
    private LongConsumer documentLinkAction(BulkLinkMode mode, DocumentLinkKind kind, long documentId,
                                            ProjectApiCaller caller) {
        return switch (mode) {
            case LINK -> id -> documentLinksOpenApiService.link(kind, id, documentId, caller);
            case UNLINK -> id -> documentLinksOpenApiService.unlink(kind, id, documentId, caller);
        };
    }

    /**
     * A job the caller started: its progress and, once done, its result.
     *
     * @param jobId  the job's id
     * @param caller the authenticated person
     * @return the job's current state
     * @throws ResponseStatusException 404 when unknown, forgotten, or started by someone else
     */
    public BulkJobResource job(String jobId, PersonDTO caller) {
        return jobRegistry.find(jobId, ownerOf(caller))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tâche introuvable : " + jobId));
    }

    /**
     * Asks to stop a job the caller started: it stops before its next element, the elements processed
     * until then staying changed.
     *
     * @param jobId  the job's id
     * @param caller the authenticated person
     * @throws ResponseStatusException 404 when unknown, already ended, or started by someone else
     */
    public void cancel(String jobId, PersonDTO caller) {
        if (!jobRegistry.cancel(jobId, ownerOf(caller))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune action en cours : " + jobId);
        }
    }

    /**
     * Registers a job for the caller and hands its elements to the background runner.
     */
    private BulkJobResource startJob(ProjectApiCaller caller, List<Long> ids, LongConsumer action) {
        BulkJob job = jobRegistry.start(ownerOf(caller.person()), ids.size());
        runner.run(job, ids, action);
        return job.toResource();
    }

    /**
     * The person a job belongs to: the caller.
     */
    private static long ownerOf(PersonDTO person) {
        if (person == null || person.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise");
        }
        return person.getId();
    }

    /**
     * The targets indexed by their collection's path segment.
     */
    private static <T extends BulkCollectionTarget> Map<String, T> byCollection(List<T> targets) {
        return targets.stream().collect(Collectors.toMap(BulkCollectionTarget::collection, Function.identity()));
    }

    /**
     * The collection's target for a kind of bulk action, or 404 for a collection without that action.
     */
    private static <T> T targetOf(Map<String, T> targetsByCollection, String collection) {
        T target = targetsByCollection.get(collection);
        if (target == null) {
            throw unknownCollection(collection);
        }
        return target;
    }

    /**
     * The 404 of a collection with no bulk action of the requested kind.
     */
    private static ResponseStatusException unknownCollection(String collection) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection inconnue : " + collection);
    }

    /**
     * The ids to act on, each once and in order: at least one, at most {@link #MAX_IDS}.
     */
    private static List<Long> idsOf(List<Long> requested) {
        List<Long> ids = distinctIds(requested);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids est obligatoire et ne peut être vide");
        }
        if (ids.size() > MAX_IDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Au plus " + MAX_IDS + " éléments par action en lot");
        }
        return ids;
    }

    /**
     * The requested ids without nulls nor repeats, in the order they came.
     */
    private static List<Long> distinctIds(List<Long> requested) {
        if (requested == null) {
            return List.of();
        }
        return requested.stream().filter(Objects::nonNull).distinct().toList();
    }

    /**
     * The patch to apply: an object, without an identifier (unique per element, never the same for several).
     */
    private static JsonNode requireBulkPatch(JsonNode patch) {
        if (patch == null || !patch.isObject() || patch.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patch est obligatoire");
        }
        if (patch.has(IDENTIFIER_PROPERTY)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'identifiant ne peut pas être modifié en lot");
        }
        return patch;
    }
}
