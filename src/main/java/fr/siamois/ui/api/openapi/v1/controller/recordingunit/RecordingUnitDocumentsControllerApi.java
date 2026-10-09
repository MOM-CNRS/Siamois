package fr.siamois.ui.api.openapi.v1.controller.recordingunit;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
import fr.siamois.ui.api.openapi.v1.service.DocumentLinksOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.FieldQueryService;
import org.springframework.util.MultiValueMap;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentListResponse;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recording-units")
@Tag(name = OpenApiTags.RECORDING_UNIT)
@RequiredArgsConstructor
public class RecordingUnitDocumentsControllerApi {

    private final ProjectApiService projectApiService;
    private final DocumentWriteOpenApiService documentWriteOpenApiService;
    private final DocumentLinksOpenApiService documentLinksOpenApiService;
    private final FieldQueryService fieldQueryService;

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Créer un document rattaché à une unité d'enregistrement",
            description = "Crée un document et le lie à l'UE (recording_unit_document). "
                    + "Champs multipart : title (obligatoire), file (obligatoire), description, "
                    + "natureConceptId, scaleConceptId, formatConceptId. "
                    + "Modification et suppression : /api/v1/documents/{id}. Téléchargement : /api/v1/documents/{id}/file."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE ou concept introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentResponse> createRecordingUnitDocument(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) .",
                    schema = @Schema(type = "string", example = "42")
            )
            @PathVariable("id") String id,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "natureConceptId", required = false) Long natureConceptId,
            @RequestParam(value = "scaleConceptId", required = false) Long scaleConceptId,
            @RequestParam(value = "formatConceptId", required = false) Long formatConceptId,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        var resource = documentWriteOpenApiService.createForRecordingUnit(
                caller, id, title, description, natureConceptId, scaleConceptId, formatConceptId, file, lang);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DocumentResponse(resource));
    }




    @ValuesLimit.Param(defaultValue = ValuesLimit.LIST_DEFAULT)
    @GetMapping("/{id}/documents")
    @Operation(
            summary = "Documents rattachés à une unité d'enregistrement",
            description = "Liste des documents liés à l'UE via recording_unit_document. "
                    + "Même clé d'UE que GET /api/v1/recording-units/{id} (identifiant numérique ou full_identifier). "
                    + "Sans `limit` : tous les documents, sans pagination (contrat historique du mobile). Avec `limit` : liste "
                    + "paginée (offset, limit), tri (sort : identifier, title, creationTime, id ; défaut identifier:asc), recherche "
                    + "sur identifier (search), filtres f.<clé> par colonne et projection `fields` dans answers."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Pagination invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentListResponse> getDocuments(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "42")
            )
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Recherche libre, sur identifier")
            @RequestParam(required = false) String search,
            @Parameter(description = "Tri, ex. identifier:asc ou title:desc")
            @RequestParam(defaultValue = "identifier:asc") String sort,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParams,
            @Parameter(description = "Projection des champs de formulaire dans answers : \"all\" ou une liste d'ids séparés "
                    + "par des virgules. Absent : pas de clé answers.")
            @RequestParam(required = false) String fields,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        if (limit == null) {
            // The historical (mobile) contract: everything, unpaged.
            List<DocumentResource> documents = projectApiService.listDocumentsForAccessibleRecordingUnit(caller, id);
            return ResponseEntity.ok(new DocumentListResponse(documents, null));
        }
        projectApiService.validatePagedListRequest(offset, limit);
        long recordingUnitId = projectApiService.requireViewableRecordingUnit(caller, id).getId();
        DocumentListResponse body = documentLinksOpenApiService.page(DocumentLinkKind.RECORDING_UNIT, recordingUnitId, caller,
                offset, limit, sort, search, fieldQueryService.parse(Document.class, queryParams, sort, acceptLanguage), fields,
                ProjectApiService.primaryAcceptLanguage(acceptLanguage));
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(body.getMeta().total()))
                .body(body);
    }

}
