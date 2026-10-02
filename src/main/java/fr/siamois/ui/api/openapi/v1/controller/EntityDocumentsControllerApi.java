package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentListResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentLinksOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.FieldQueryService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Documents tab of the fiches of finds, places, phases and containers (the recording unit's own list lives in
 * {@code RecordingUnitDocumentsControllerApi}, which keeps the historical mobile contract), and the links between a
 * document and any of those entities and recording units.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = OpenApiTags.DOCUMENT)
@RequiredArgsConstructor
public class EntityDocumentsControllerApi {

    private final ProjectApiService projectApiService;
    private final DocumentLinksOpenApiService documentLinksOpenApiService;
    private final FieldQueryService fieldQueryService;

    private static DocumentLinkKind kindOf(String segment) {
        return DocumentLinkKind.ofPathSegment(segment)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection inconnue : " + segment));
    }

    @ValuesLimit.Param(defaultValue = ValuesLimit.LIST_DEFAULT)
    @GetMapping("/{segment:finds|phases|containers|places}/{id}/documents")
    @Operation(
            summary = "Documents liés à un mobilier, une phase, un contenant ou un lieu",
            description = "Liste paginée (offset, limit), triée (sort : identifier, title, creationTime, id ; défaut identifier:asc), "
                    + "recherche sur identifier (search), filtres f.<clé> par colonne et projection `fields` dans answers. "
                    + "Chaque élément porte _permissions, resourceUri et bookmarked."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Pagination invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Élément introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentListResponse> getDocuments(
            @PathVariable("segment") String segment,
            @PathVariable("id") long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Recherche libre, sur identifier")
            @RequestParam(required = false) String search,
            @Parameter(description = "Tri, ex. identifier:asc ou title:desc")
            @RequestParam(defaultValue = "identifier:asc") String sort,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParams,
            @Parameter(description = "Projection des champs de formulaire dans answers : \"all\" ou une liste d'ids séparés "
                    + "par des virgules. Absent : pas de clé answers.")
            @RequestParam(required = false) String fields,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        DocumentListResponse body = documentLinksOpenApiService.page(kindOf(segment), id, caller, offset, limit, sort, search,
                fieldQueryService.parse(Document.class, queryParams, sort, acceptLanguage), fields,
                ProjectApiService.primaryAcceptLanguage(acceptLanguage));
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(body.getMeta().total()))
                .body(body);
    }

    @PutMapping("/{segment:recording-units|finds|phases|containers|places}/{id}/documents/{documentId}")
    @Operation(
            summary = "Lier un document existant à un élément",
            description = "Idempotent. Le document doit appartenir au même projet que l'élément (un lieu n'a pas de projet : "
                    + "tout document de l'organisation convient). Droit d'édition des documents du projet du document requis."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Lié (ou déjà lié)"),
            @ApiResponse(responseCode = "400", description = "Projets différents"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Pas le droit d'éditer les documents du projet"),
            @ApiResponse(responseCode = "404", description = "Élément ou document introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> link(@PathVariable("segment") String segment, @PathVariable("id") long id,
                                     @PathVariable("documentId") long documentId) {
        documentLinksOpenApiService.link(kindOf(segment), id, documentId, projectApiService.requireCaller());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{segment:recording-units|finds|phases|containers|places}/{id}/documents/{documentId}")
    @Operation(
            summary = "Retirer le lien entre un document et un élément",
            description = "Idempotent. Le document n'est pas supprimé : seul le lien l'est. "
                    + "Droit d'édition des documents du projet du document requis."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Lien retiré (ou absent)"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Pas le droit d'éditer les documents du projet"),
            @ApiResponse(responseCode = "404", description = "Élément ou document introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> unlink(@PathVariable("segment") String segment, @PathVariable("id") long id,
                                       @PathVariable("documentId") long documentId) {
        documentLinksOpenApiService.unlink(kindOf(segment), id, documentId, projectApiService.requireCaller());
        return ResponseEntity.noContent().build();
    }
}
