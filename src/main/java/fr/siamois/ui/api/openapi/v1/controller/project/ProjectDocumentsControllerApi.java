package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.domain.models.document.Document;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.DocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.service.DocumentListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.FieldQueryService;
import fr.siamois.ui.api.openapi.v1.service.ResourceBookmarkService;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentListResponse;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{id}/documents")
@Tag(name = OpenApiTags.PROJECT)
@Tag(name = OpenApiTags.DOCUMENT)
@RequiredArgsConstructor
public class ProjectDocumentsControllerApi {

    private final ProjectApiService projectApiService;
    private final DocumentWriteOpenApiService documentWriteOpenApiService;
    private final DocumentOpenApiMapper documentOpenApiMapper;
    private final DocumentListProjectionService documentListProjectionService;
    private final ResourceBookmarkService resourceBookmarkService;
    private final FieldQueryService fieldQueryService;


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Créer un document rattaché à un projet",
            description = "Crée un document et le lie au projet. "
                    + "Champs multipart : title (obligatoire), file (obligatoire), description, "
                    + "natureConceptId, scaleConceptId, formatConceptId."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet ou concept introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentResponse> createProjectDocument(
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
        var resource = documentWriteOpenApiService.createForProject(
                caller, id, title, description, natureConceptId, scaleConceptId, formatConceptId, file, lang);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DocumentResponse(resource));
    }

    @ValuesLimit.Param(defaultValue = ValuesLimit.LIST_DEFAULT)
    @GetMapping()
    @Operation(
            summary = "Documents d'un projet",
            description = "Documents du projet (colonne de rattachement). Même identifiant de projet que GET /api/v1/projects/{id}. "
                    + "Sans `limit` : tous les documents, sans pagination "
                    + "(contrat historique du mobile). Avec `limit` : liste paginée (offset, limit), tri (sort : identifier, "
                    + "title, creationTime, id ; défaut identifier:asc), recherche sur identifier (search), filtres f.<clé> par "
                    + "colonne et projection `fields` dans answers."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentListResponse> getDocuments(
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
            List<DocumentResource> documents = projectApiService.listDocumentsForAccessibleProject(caller, id);
            return ResponseEntity.ok(new DocumentListResponse(documents, null));
        }
        projectApiService.validatePagedListRequest(offset, limit);
        Page<DocumentDTO> page = projectApiService.pageDocumentsForProject(caller, id, offset, limit, sort, search,
                fieldQueryService.parse(Document.class, queryParams, sort, acceptLanguage));

        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        ProjectResourcePermissions permissions = ProjectResourcePermissions.of(
                        projectApiService.canEditDocumentsForProject(caller, id, lang))
                .withValidate(projectApiService.canValidateForProject(caller, id, lang));
        DocumentListProjectionService.DocumentListProjection projection =
                documentListProjectionService.build(page.getContent(), fields, lang);

        List<DocumentResource> resources = page.getContent().stream()
                .map(dto -> {
                    DocumentResource resource = documentOpenApiMapper.toResource(dto, lang, projection.resolvedLabels());
                    if (fields != null) resource.setAnswers(projection.answersFor(dto.getId()));
                    resource.setPermissions(permissions);
                    return resource;
                })
                .toList();
        // Every row shares this project, hence one institution — one bookmark query for the page.
        if (!page.isEmpty()) {
            resourceBookmarkService.markBookmarked(caller.person(),
                    page.getContent().get(0).getActionUnit().getCreatedByInstitution(), resources, lang);
        }
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(new DocumentListResponse(resources, new ListMeta(page.getTotalElements(), limit, (long) offset)));
    }


}
