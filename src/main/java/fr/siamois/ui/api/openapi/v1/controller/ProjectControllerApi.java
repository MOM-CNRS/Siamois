package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectResponseMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.document.ProjectDocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.FindListResponse;
import fr.siamois.ui.api.openapi.v1.response.ProjectListResponse;
import fr.siamois.ui.api.openapi.v1.response.ProjectResponse;
import fr.siamois.ui.api.openapi.v1.response.RecordingUnitListResponse;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectDocumentsData;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectDocumentsResponse;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.hidden.Hidden;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@Tags({@Tag(name = "Project", description = "Endpoints des projets")})
public class ProjectControllerApi {

    private final ProjectApiService projectApiService;
    private final ProjectResponseMapper projectResponseMapper;
    private final RecordingUnitResponseMapper recordingUnitResourceMapper;

    public ProjectControllerApi(ProjectApiService projectApiService,
                                ProjectResponseMapper projectResponseMapper,
                                RecordingUnitResponseMapper recordingUnitResourceMapper) {
        this.projectApiService = projectApiService;
        this.projectResponseMapper = projectResponseMapper;
        this.recordingUnitResourceMapper = recordingUnitResourceMapper;
    }

    @GetMapping
    @Operation(summary = "La liste des projets")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation non autorisée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<ProjectListResponse> getAll(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name:asc") String sort,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        Page<AccessibleProjectForApi> rows = projectApiService.pageAccessibleProjects(
                caller, organizationId, search, offset, limit, sort);

        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        List<ProjectResource> resources = rows.getContent().stream()
                .map(row -> projectResponseMapper.toResource(row, lang))
                .toList();

        ListMeta meta = new ListMeta(rows.getTotalElements(), limit, (long) offset);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(rows.getTotalElements()))
                .body(new ProjectListResponse(resources, meta));
    }

    @Operation(summary = "Un projet via son identifiant",
            description = "Clé d'URL : id numérique (clé primaire), identifiant métier complet (fullIdentifier), "
                    + "ou identifiant court du projet dans une de vos organisations. "
                    + "Les caractères réservés (ex. « / ») doivent être encodés pour l'URL.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}")
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<ProjectResponse> getById(@PathVariable("id") String id,
                                                   @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false)
                                                   String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        AccessibleProjectForApi row = projectApiService.requireAccessibleProject(caller, id);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        ProjectResource resource = projectResponseMapper.toResource(row, lang);
        return ResponseEntity.ok(new ProjectResponse(resource));
    }

    @Operation(
            summary = "Documents rattachés à un projet",
            description = "Liste des documents liés à l'unité d'action (projet) via action_unit_document. "
                    + "Même clé de projet que GET /api/v1/projects/{id} (id numérique, fullIdentifier, identifiant court)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/documents")
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<ProjectDocumentsResponse> getDocuments(@PathVariable("id") String id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<ProjectDocumentResource> documents = projectApiService.listDocumentsForAccessibleProject(caller, id);
        return ResponseEntity.ok(new ProjectDocumentsResponse(new ProjectDocumentsData(documents)));
    }

    @Hidden
    @GetMapping("/{id}/finds")
    public ResponseEntity<FindListResponse> getFinds(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Hidden
    @GetMapping(value = "/{id}/geopackage", produces = "application/geopackage+sqlite3")
    public ResponseEntity<Resource> getGeoPackage(@PathVariable String id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Operation(summary = "Récupérer la liste paginée des unités d'enregistrement d'un projet",
            description = "Clé de projet : identique à GET /api/v1/projects/{id} (id numérique, fullIdentifier, identifiant court). "
                    + "Tri : paramètre sort au format « propriété:asc » ou « propriété:desc » "
                    + "(propriétés autorisées : creationTime, id, identifier, fullIdentifier, openingDate, closingDate). "
                    + "Valeur par défaut : creationTime:desc.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/recording-units")
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<RecordingUnitListResponse> getList(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Tri, ex. fullIdentifier:asc ou creationTime:desc")
            @RequestParam(defaultValue = "creationTime:desc") String sort) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        Page<RecordingUnitDTO> page = projectApiService.pageRecordingUnitsForProject(caller, id, offset, limit, sort);

        List<RecordingUnitResource> resources = page.getContent().stream()
                .map(recordingUnitResourceMapper::convert)
                .toList();

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(new RecordingUnitListResponse(resources, meta));
    }

}
