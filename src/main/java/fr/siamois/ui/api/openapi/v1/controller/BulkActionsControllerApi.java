package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkDeleteRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkDocumentLinkRequest;
import fr.siamois.ui.api.openapi.v1.request.bulk.BulkPatchRequest;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkResultResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bulk actions on the selection of a list: delete, patch and link or unlink a document on many
 * elements of one collection in one request. Each element is treated as its unit endpoint would
 * treat it (same rights, same rules, its own transaction); the answer says which ones succeeded and
 * why the others failed.
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
     * @return which elements were deleted, and why the others were not
     */
    @PostMapping(value = "/{collection}/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Supprimer des éléments en lot",
            description = "Supprime chaque élément comme le DELETE unitaire de la collection (mêmes droits, même refus "
                    + "409 tant qu'un élément a des relations), chacun dans sa propre transaction. "
                    + "Un élément refusé n'empêche pas la suppression des autres : le bilan les liste."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bilan : éléments supprimés et éléments en échec"),
            @ApiResponse(responseCode = "400", description = "ids vide ou trop nombreux"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Collection inconnue"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<BulkResultResponse> delete(
            @Parameter(description = "Collection, ex. recording-units") @PathVariable("collection") String collection,
            @RequestBody BulkDeleteRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new BulkResultResponse(
                bulkActionOpenApiService.delete(collection, body, caller, acceptLanguage)));
    }

    /**
     * Applies the same partial update to many elements of a collection.
     *
     * @param collection     the collection's path segment
     * @param body           the elements and the update
     * @param acceptLanguage the request's language
     * @return which elements were updated, and why the others were not
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
            @ApiResponse(responseCode = "200", description = "Bilan : éléments modifiés et éléments en échec"),
            @ApiResponse(responseCode = "400", description = "ids vide ou trop nombreux, patch absent ou portant un identifiant"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Collection inconnue"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<BulkResultResponse> patch(
            @Parameter(description = "Collection, ex. recording-units") @PathVariable("collection") String collection,
            @RequestBody BulkPatchRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new BulkResultResponse(
                bulkActionOpenApiService.patch(collection, body, caller, acceptLanguage)));
    }

    /**
     * Links a document to many elements, or removes that link from each.
     *
     * @param segment        the elements' collection path segment
     * @param body           the elements, the document and what to do
     * @param acceptLanguage the request's language
     * @return which elements were changed, and why the others were not
     */
    @PostMapping(value = "/{segment}/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Lier ou délier un document en lot",
            description = "mode LINK : lie le document à chaque élément ; UNLINK : retire ce lien (le document n'est pas "
                    + "supprimé). Idempotent, mêmes règles que PUT/DELETE /api/v1/{segment}/{id}/documents/{documentId}, "
                    + "et en plus le droit de modifier chaque élément (403 sinon, dans le bilan)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bilan : éléments traités et éléments en échec"),
            @ApiResponse(responseCode = "400", description = "ids vide ou trop nombreux, documentId absent"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Collection inconnue"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<BulkResultResponse> linkDocument(
            @Parameter(description = "Collection, ex. recording-units") @PathVariable("segment") String segment,
            @RequestBody BulkDocumentLinkRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new BulkResultResponse(
                bulkActionOpenApiService.linkDocument(segment, body, caller, acceptLanguage)));
    }
}
