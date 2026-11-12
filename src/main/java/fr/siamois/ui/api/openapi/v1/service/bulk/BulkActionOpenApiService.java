package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkDeleteRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkDocumentLinkRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkLinkMode;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkPatchRequest;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkResultResource;
import fr.siamois.ui.api.openapi.v1.service.DocumentLinksOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

/**
 * The bulk actions on a list's selection: delete, patch (a field, a relation added or removed) and
 * link or unlink a document, on many elements of one collection in one request.
 *
 * <p>Deliberately NOT transactional: each element goes through its collection's unit operation, in
 * that operation's own transaction, with its own rights and rules. An element that is refused (a
 * relation still holding it, no right to edit it…) is reported in the result and leaves the others
 * untouched, which is what a user acting on a selection expects.</p>
 */
@Service
public class BulkActionOpenApiService {

    /** The most elements one bulk request may act on. */
    public static final int MAX_IDS = 1000;

    private static final String IDENTIFIER_PROPERTY = "identifier";

    private final Map<String, BulkEntityOperations> operationsByCollection;
    private final DocumentLinksOpenApiService documentLinksOpenApiService;
    private final BulkFailureTranslator failureTranslator;

    /**
     * Indexes every collection's operations by its path segment.
     *
     * @param operations                  one per collection that supports bulk actions
     * @param documentLinksOpenApiService the document links' unit service
     * @param failureTranslator           how an element's failure is reported
     */
    public BulkActionOpenApiService(List<BulkEntityOperations> operations,
                                    DocumentLinksOpenApiService documentLinksOpenApiService,
                                    BulkFailureTranslator failureTranslator) {
        this.operationsByCollection = operations.stream()
                .collect(Collectors.toMap(BulkEntityOperations::collection, Function.identity()));
        this.documentLinksOpenApiService = documentLinksOpenApiService;
        this.failureTranslator = failureTranslator;
    }

    /**
     * Deletes each element, as its unit DELETE would.
     *
     * @param collection     the collection's path segment
     * @param request        the elements to delete
     * @param caller         the authenticated caller
     * @param acceptLanguage the request's Accept-Language header
     * @return which elements were deleted, and why the others were not
     */
    public BulkResultResource delete(String collection, BulkDeleteRequest request, ProjectApiCaller caller,
                                     String acceptLanguage) {
        BulkEntityOperations operations = operationsOf(collection);
        return forEach(idsOf(request.getIds()), id -> operations.delete(caller, id, acceptLanguage));
    }

    /**
     * Applies the same partial update to each element, as its unit PATCH would.
     *
     * @param collection     the collection's path segment
     * @param request        the elements and the update
     * @param caller         the authenticated caller
     * @param acceptLanguage the request's Accept-Language header
     * @return which elements were updated, and why the others were not
     */
    public BulkResultResource patch(String collection, BulkPatchRequest request, ProjectApiCaller caller,
                                    String acceptLanguage) {
        BulkEntityOperations operations = operationsOf(collection);
        JsonNode patch = requireBulkPatch(request.getPatch());
        return forEach(idsOf(request.getIds()), id -> operations.patch(caller, id, patch, acceptLanguage));
    }

    /**
     * Links a document to each element, or removes that link from each.
     *
     * @param segment the elements' collection path segment
     * @param request the elements, the document and what to do
     * @param caller  the authenticated caller
     * @return which elements were changed, and why the others were not
     */
    public BulkResultResource linkDocument(String segment, BulkDocumentLinkRequest request, ProjectApiCaller caller) {
        DocumentLinkKind kind = DocumentLinkKind.ofPathSegment(segment)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection inconnue : " + segment));
        long documentId = requireDocumentId(request.getDocumentId());
        BulkLinkMode mode = Objects.requireNonNullElse(request.getMode(), BulkLinkMode.LINK);
        return forEach(idsOf(request.getIds()), documentLinkAction(mode, kind, documentId, caller));
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
     * Runs the action on each id, one after the other, collecting each success and each failure.
     */
    private BulkResultResource forEach(List<Long> ids, LongConsumer action) {
        BulkResultBuilder result = new BulkResultBuilder(failureTranslator);
        ids.forEach(id -> result.attempt(id, action));
        return result.build();
    }

    /**
     * The operations of the collection, or 404 for a collection with no bulk actions.
     */
    private BulkEntityOperations operationsOf(String collection) {
        BulkEntityOperations operations = operationsByCollection.get(collection);
        if (operations == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection inconnue : " + collection);
        }
        return operations;
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

    /**
     * The document to link or unlink.
     */
    private static long requireDocumentId(Long documentId) {
        if (documentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentId est obligatoire");
        }
        return documentId;
    }
}
