package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectResponseMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.document.ProjectDocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.FindListResponse;
import fr.siamois.ui.api.openapi.v1.response.ProjectListResponse;
import fr.siamois.ui.api.openapi.v1.response.ProjectResponse;
import fr.siamois.ui.api.openapi.v1.response.RecordingUnitListResponse;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentResourceResponse;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectDocumentsData;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectDocumentsResponse;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectFormResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = OpenApiTags.PROJECT)
@RequiredArgsConstructor
public class ProjectControllerApi {

    private final ProjectApiService projectApiService;
    private final ProjectResponseMapper projectResponseMapper;
    private final RecordingUnitResponseMapper recordingUnitResourceMapper;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    private final DocumentWriteOpenApiService documentWriteOpenApiService;

    @GetMapping
    @Operation(summary = "La liste des projets")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation non autorisée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ProjectListResponse> getAll(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String search,
            @Parameter(description = "Champ de tri : id, name, identifier, creationTime ; direction asc ou desc (ex. name:asc, id:desc).")
            @RequestParam(name = "name:asc", required = false) List<String> sort,
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

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer un projet",
            description = "Crée une unité d'action (projet) dans une organisation. "
                    + "Droit requis : gestionnaire d'institution ou gestionnaire d'action."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Requête ou données invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Création non autorisée ou organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation, type ou lieu introuvable"),
            @ApiResponse(responseCode = "409", description = "Conflit (nom ou identifiant déjà utilisé)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ProjectResponse> create(
            @RequestBody ProjectCreateRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        AccessibleProjectForApi row = projectApiService.createProject(caller, body, lang);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ProjectResponse(projectResponseMapper.toResource(row, lang)));
    }

    @GetMapping("/form")
    @Operation(
            summary = "Gabarit UI du formulaire projet",
            description = "Retourne le layout et la définition des champs du formulaire système projet (sans valeurs saisies). "
                    + "Vocabulaires : GET /api/v1/vocabularies."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })


    public ResponseEntity<ProjectFormResponse> getProjectUiForm(
            @Parameter(description = "Institution (doit être dans le périmètre JWT).", example = "10", required = true)
            @RequestParam("organizationId") long organizationId,
            @Parameter(description = "Langue des libellés de champs (première entrée utilisée).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(new ProjectFormResponse(
                recordingUnitOpenApiService.buildProjectUiForm(organizationId, caller.person(), lang)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Un projet via son identifiant",
            description = "Réponse enrichie : `codeOperationArcheologique` (Code OA), et pour les champs basés sur un concept "
                    + "(`typeConcept`, `actionCodeTypeConcept`, `mainLocationCategoryConcept`) le vocabulaire, "
                    + "les identifiants de concept et le libellé courant selon Accept-Language.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ProjectResponse> getById(
            @PathVariable("id") String id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        AccessibleProjectForApi row = projectApiService.requireAccessibleProject(caller, id);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(new ProjectResponse(projectResponseMapper.toResource(row, lang)));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Mise à jour partielle d'un projet",
            description = "Champs modifiables : nom, catégorie (type d'opération, "
                    + "`typeConceptId`), date de début, date de fin, localisation précise (`spatialContextSpatialUnitIds`, "
                    + "liste d'identifiants d'unités spatiales de l'organisation ; tableau vide = tout retirer). "
                    + "Champs absents = inchangés, champ null = valeur effacé . Droit d'écriture sur le projet requis.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Requête ou données invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Modification non autorisée"),
            @ApiResponse(responseCode = "404", description = "Projet, type ou lieu introuvable, ou non accessible"),
            @ApiResponse(responseCode = "409", description = "Conflit (ex. nom ou identifiant déjà utilisé)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ProjectResponse> patch(
            @PathVariable("id") String id,
            @RequestBody ProjectPatchRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        AccessibleProjectForApi row = projectApiService.patchProject(caller, id, body, lang);
        return ResponseEntity.ok(new ProjectResponse(projectResponseMapper.toResource(row, lang)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un projet",
            description = "Uniquement si le projet n'a ni unité d'enregistrement ni sous-projet. "
                    + "Même clé de projet que GET /api/v1/projects/{id}. Droit d'écriture requis.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Supprimé"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Suppression non autorisée"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "409", description = "Projet non supprimable (UE ou sous-projets)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> delete(
            @PathVariable("id") String id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        projectApiService.deleteProject(caller, id, lang);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Créer un document rattaché à un projet",
            description = "Crée un document et le lie au projet (action_unit_document). "
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
    public ResponseEntity<DocumentResourceResponse> createProjectDocument(
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
                .body(new DocumentResourceResponse(resource));
    }

    @GetMapping("/{id}/documents")
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
    public ResponseEntity<ProjectDocumentsResponse> getDocuments(@PathVariable("id") String id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<ProjectDocumentResource> documents = projectApiService.listDocumentsForAccessibleProject(caller, id);
        return ResponseEntity.ok(new ProjectDocumentsResponse(new ProjectDocumentsData(documents)));
    }

    @GetMapping("/{id}/recording-units")
    @Operation(summary = "Récupérer la liste paginée des unités d'enregistrement d'un projet",
            description = "Clé de projet : identique à GET /api/v1/projects/{id} (id numérique, fullIdentifier, identifiant court). "
                    + "Tri : paramètre sort au format « propriété:asc » ou « propriété:desc » "
                    + "(propriétés autorisées : creationTime, id, identifier, fullIdentifier, openingDate, closingDate). "
                    + "Valeur par défaut : creationTime:desc. "
                    + "Chaque élément inclut notamment : identifiant, type, nombre de relations stratigraphiques, "
                    + "nombre de mobiliers, dates, lieu (référence place), couleur de matrice, auteur et contributeurs.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
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

    @Hidden
    @GetMapping("/{id}/mobiliers")
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
}
