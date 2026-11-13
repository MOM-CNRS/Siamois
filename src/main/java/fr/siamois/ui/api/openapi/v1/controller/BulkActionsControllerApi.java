package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkDeleteRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkDocumentLinkRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkDuplicateRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkPatchRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkStratigraphyRequest;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.bulk.BulkActionOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bulk actions on the selection of a list: delete, patch and link or unlink a document on many
 * elements of one collection in one request. The request is checked at once, then its elements are
 * processed in the background: the answer (202) is a job to poll on {@code GET /api/v1/bulk/jobs/{id}}
 * for progress and, once done, which elements succeeded and why the others failed. Each element is
 * treated as its unit endpoint would treat it (same rights, same rules, its own transaction).
 *
 * <p>The collection is a plain path variable, not a fixed list: the collections served are those
 * that declare a {@code BulkEntityOperations} bean (an unknown one answers 404), so supporting
 * another collection never touches this controller. Under its own {@code /bulk} root, these routes
 * can't collide with any {@code /{collection}/{id}/…} route of the unit controllers.</p>
 */
@RestController
@RequestMapping("/api/v1/bulk")
@Tag(name = OpenApiTags.PROJECT)
@RequiredArgsConstructor
public class BulkActionsControllerApi {

    private final ProjectApiService projectApiService;
    private final BulkActionOpenApiService bulkActionOpenApiService;

    /**
     * Deletes many elements of a collection.
     *
     * @param collection     the collection's path segment
     * @param body           the elements to delete
     * @param acceptLanguage the request's language
     * @return the job deleting them, to poll
     */
    @PostMapping(value = "/{collection}/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Supprimer des éléments en lot",
            description = "Supprime chaque élément comme le DELETE unitaire de la collection (mêmes droits, même refus "
                    + "409 tant qu'un élément a des relations), chacun dans sa propre transaction. "
                    + "Un élément refusé n'empêche pas la suppression des autres : le bilan les liste."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Tâche démarrée, à suivre sur GET /api/v1/bulk/jobs/{id}"),
            @ApiResponse(responseCode = "409", description = "Une action en lot de l'appelant est déjà en cours"),
            @ApiResponse(responseCode = "400", description = "ids vide ou trop nombreux"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Collection inconnue"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<BulkJobResponse> delete(
            @Parameter(description = "Collection, ex. recording-units") @PathVariable("collection") String collection,
            @RequestBody BulkDeleteRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return accepted(bulkActionOpenApiService.delete(collection, body, caller, acceptLanguage));
    }

    /**
     * Duplicates many elements of a collection, each keeping its relations but not its children.
     *
     * @param collection     the collection's path segment
     * @param body           the elements to duplicate
     * @param acceptLanguage the request's language
     * @return the job duplicating them, to poll
     */
    @PostMapping(value = "/{collection}/duplicate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Dupliquer des éléments en lot",
            description = "Duplique chaque élément comme sa duplication unitaire (mêmes droits, identifiant régénéré). "
                    + "Chaque copie garde les relations de l'élément (parents, phases, stratigraphie, documents…), mais ses enfants "
                    + "ne sont pas dupliqués. Les copies sont créées en commençant par le dernier élément, pour apparaître "
                    + "en tête des listes dans l'ordre de la sélection."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Tâche démarrée, à suivre sur GET /api/v1/bulk/jobs/{id}"),
            @ApiResponse(responseCode = "400", description = "ids vide ou trop nombreux"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Collection sans duplication"),
            @ApiResponse(responseCode = "409", description = "Une action en lot de l'appelant est déjà en cours")
    })
    public ResponseEntity<BulkJobResponse> duplicate(
            @Parameter(description = "Collection, ex. recording-units") @PathVariable("collection") String collection,
            @RequestBody BulkDuplicateRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return accepted(bulkActionOpenApiService.duplicate(collection, body, caller, acceptLanguage));
    }

    /**
     * Applies the same partial update to many elements of a collection.
     *
     * @param collection     the collection's path segment
     * @param body           the elements and the update
     * @param acceptLanguage the request's language
     * @return the job updating them, to poll
     */
    @PostMapping(value = "/{collection}/patch", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Modifier des éléments en lot",
            description = "Applique à chaque élément la même mise à jour partielle, au format du PATCH unitaire de la "
                    + "collection. Pour un champ à plusieurs valeurs (relations : UE parentes, phases, contenants…), "
                    + "{ add: [...] } et { remove: [...] } ajoutent ou retirent des valeurs sans toucher aux autres. "
                    + "L'identifiant, unique par élément, ne peut pas être modifié en lot."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Tâche démarrée, à suivre sur GET /api/v1/bulk/jobs/{id}"),
            @ApiResponse(responseCode = "409", description = "Une action en lot de l'appelant est déjà en cours"),
            @ApiResponse(responseCode = "400", description = "ids vide ou trop nombreux, patch absent ou portant un identifiant"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Collection inconnue"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<BulkJobResponse> patch(
            @Parameter(description = "Collection, ex. recording-units") @PathVariable("collection") String collection,
            @RequestBody BulkPatchRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return accepted(bulkActionOpenApiService.patch(collection, body, caller, acceptLanguage));
    }

    /**
     * Links a document to many elements, or removes that link from each.
     *
     * @param segment        the elements' collection path segment
     * @param body           the elements, the document and what to do
     * @param acceptLanguage the request's language
     * @return the job changing their links, to poll
     */
    @PostMapping(value = "/{segment}/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Lier ou délier un document en lot",
            description = "mode LINK : lie le document à chaque élément ; UNLINK : retire ce lien (le document n'est pas "
                    + "supprimé). Idempotent, mêmes règles que PUT/DELETE /api/v1/{segment}/{id}/documents/{documentId}, "
                    + "et en plus le droit de modifier chaque élément (403 sinon, dans le bilan)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Tâche démarrée, à suivre sur GET /api/v1/bulk/jobs/{id}"),
            @ApiResponse(responseCode = "409", description = "Une action en lot de l'appelant est déjà en cours"),
            @ApiResponse(responseCode = "400", description = "ids vide ou trop nombreux, documentId absent"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Collection inconnue"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<BulkJobResponse> linkDocument(
            @Parameter(description = "Collection, ex. recording-units") @PathVariable("segment") String segment,
            @RequestBody BulkDocumentLinkRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return accepted(bulkActionOpenApiService.linkDocument(segment, body, caller, acceptLanguage));
    }

    /**
     * Adds a stratigraphic relationship with the same unit to many elements, or removes it from each.
     *
     * @param segment the elements' collection path segment
     * @param body    the elements, the other unit, the relationship and what to do
     * @return the job changing their relationships, to poll
     */
    @PostMapping(value = "/{segment}/stratigraphy", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Ajouter ou retirer une relation stratigraphique en lot",
            description = "mode LINK : ajoute à chaque UE la relation (position, type, incertitude) avec l'UE "
                    + "relatedRecordingUnitId ; UNLINK : retire la relation entre chaque UE et celle-ci. Mêmes droits et "
                    + "règles que POST / DELETE /api/v1/recording-units/{id}/stratigraphic-relationships, élément par élément."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Tâche démarrée, à suivre sur GET /api/v1/bulk/jobs/{id}"),
            @ApiResponse(responseCode = "409", description = "Une action en lot de l'appelant est déjà en cours"),
            @ApiResponse(responseCode = "400", description = "ids vide ou trop nombreux, UE ou position absente"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Collection sans relations stratigraphiques"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<BulkJobResponse> linkStratigraphy(
            @Parameter(description = "Collection, ex. recording-units") @PathVariable("segment") String segment,
            @RequestBody BulkStratigraphyRequest body) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return accepted(bulkActionOpenApiService.linkStratigraphy(segment, body, caller));
    }

    /**
     * A bulk action the caller started: its progress and, once done, its result.
     *
     * @param jobId the job's id
     * @return the job's current state
     */
    @GetMapping("/jobs/{jobId}")
    @Operation(
            summary = "Suivre une action en lot",
            description = "Avancement (processed / total) et bilan élément par élément d'une action en lot démarrée par "
                    + "l'appelant. status vaut RUNNING tant que des éléments restent à traiter, puis DONE (ou FAILED si "
                    + "l'action s'est arrêtée). Pendant l'exécution, seuls les compteurs sont renvoyés ; les listes "
                    + "succeeded / failed le sont une fois la tâche terminée. Le bilan reste disponible une heure après la fin."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Tâche inconnue, expirée ou démarrée par quelqu'un d'autre")
    })
    public ResponseEntity<BulkJobResponse> job(@PathVariable("jobId") String jobId) {
        // Polled often: only who asks is needed, not their organizations.
        return ResponseEntity.ok(new BulkJobResponse(bulkActionOpenApiService.job(jobId, projectApiService.requirePerson())));
    }

    /**
     * Asks to stop a bulk action the caller started.
     *
     * @param jobId the job's id
     * @return 204 once the stop is asked
     */
    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(
            summary = "Arrêter une action en lot",
            description = "L'action s'arrête avant l'élément suivant (status CANCELLED) ; les éléments déjà traités restent "
                    + "modifiés et figurent dans le bilan."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Arrêt demandé"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Tâche inconnue, terminée ou démarrée par quelqu'un d'autre")
    })
    public ResponseEntity<Void> cancel(@PathVariable("jobId") String jobId) {
        bulkActionOpenApiService.cancel(jobId, projectApiService.requirePerson());
        return ResponseEntity.noContent().build();
    }

    /**
     * The 202 of a job just started, pointing at where to follow it.
     */
    private static ResponseEntity<BulkJobResponse> accepted(BulkJobResource job) {
        return ResponseEntity.accepted()
                .location(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/bulk/jobs/{id}").buildAndExpand(job.id()).toUri())
                .body(new BulkJobResponse(job));
    }
}
