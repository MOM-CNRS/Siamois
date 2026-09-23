package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectResponseMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectListResponse;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectResponse;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectSiblingsResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectListProjectionService;
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
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = OpenApiTags.PROJECT)
@RequiredArgsConstructor
public class ProjectControllerApi {

    private final ProjectApiService projectApiService;
    private final ProjectResponseMapper projectResponseMapper;
    private final RecordingUnitResponseMapper recordingUnitResourceMapper;
    private final DocumentWriteOpenApiService documentWriteOpenApiService;
    private final ProjectListProjectionService projectListProjectionService;

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
            @Parameter(description = "Recherche par identifiant, peu-être utilisé pour l'autocompletion.")
            @RequestParam(required = false) String search,
            @Parameter(description = "Champ de tri \"champ:direction\" : id, name, identifier, fullIdentifier, "
                    + "beginDate, endDate, creationTime, recordingUnitCount ; direction asc ou desc "
                    + "(ex. name:asc, recordingUnitCount:desc). 400 si le champ est inconnu.")
            @RequestParam(defaultValue = "name:asc") String sort,
            @Parameter(description = "Projection des champs de formulaire dans answers : \"all\", \"default\" "
                    + "(colonnes visibles par défaut de la liste), ou une liste d'ids de champs séparés par "
                    + "des virgules. Absent : pas de clé answers, la liste reste au coût d'avant.")
            @RequestParam(required = false) String fields,
            @Parameter(hidden = true)
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        // f.<key>[.from|.to] — see ProjectListFilter for the full contract. Parsed from the raw
        // query params (not a dedicated @RequestParam per key) since the filterable-column set is
        // a whitelist, not a fixed handful of named parameters.
        ProjectListFilter filter = ProjectListFilter.parse(queryParams);
        Page<AccessibleProjectForApi> rows = projectApiService.pageAccessibleProjects(
                caller, organizationId, search, offset, limit, sort, filter);

        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        // Both batched once for the whole page, not per row — see ProjectApiService#permissionsFor /
        // #bookmarkedResourceUris.
        Map<Long, ProjectResourcePermissions> permissionsByActionUnitId =
                projectApiService.permissionsFor(caller, rows.getContent());
        Set<String> bookmarkedUris = projectApiService.bookmarkedResourceUris(caller, rows.getContent(), lang);
        // Libellés de concepts et projection answers : un seul lot pour la page, jamais par ligne.
        ProjectListProjectionService.ProjectListProjection projection =
                projectListProjectionService.build(rows.getContent(), fields, lang);

        List<ProjectResource> resources = rows.getContent().stream()
                .map(row -> {
                    Long id = row.actionUnit().getId();
                    ProjectResourcePermissions permissions = permissionsByActionUnitId.getOrDefault(
                            id, ProjectResourcePermissions.of(false));
                    boolean bookmarked = bookmarkedUris.contains(ProjectApiService.actionUnitResourceUri(id));
                    return projectResponseMapper.toResource(row, lang, permissions, bookmarked,
                            projection.resolvedLabels(), projection.answersFor(id));
                })
                .toList();

        ListMeta meta = new ListMeta(rows.getTotalElements(), limit, (long) offset);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(rows.getTotalElements()))
                .body(new ProjectListResponse(resources, meta));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Un projet via son identifiant",
            description = "")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ProjectResponse> getById(
            @PathVariable("id") String id,
            @Parameter(description = "Projection des champs de formulaire dans answers : \"all\", \"default\" "
                    + "ou une liste d'ids de champs séparés par des virgules. Absent : pas de clé answers. "
                    + "La fiche projet demande \"all\" : contrairement à la liste, elle affiche tout le "
                    + "formulaire, pas une sélection de colonnes.")
            @RequestParam(required = false) String fields,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        AccessibleProjectForApi row = projectApiService.requireAccessibleProject(caller, id);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        ProjectResourcePermissions permissions = projectApiService.permissionsFor(caller, row);
        boolean bookmarked = projectApiService.isBookmarked(caller, row, lang);
        // Même service par lot que la liste, sur une page d'une seule ligne : les libellés de concepts
        // des champs projetés se résolvent en un lot, pas un appel par champ.
        ProjectListProjectionService.ProjectListProjection projection =
                projectListProjectionService.build(List.of(row), fields, lang);
        ProjectResource resource = projectResponseMapper.toResource(
                row, lang, permissions, bookmarked,
                projection.resolvedLabels(), projection.answersFor(row.actionUnit().getId()));
        // Only the detail response pays for this extra count query — a list page never shows it.
        if (resource.getCount() != null) {
            resource.getCount().setFinds(projectApiService.countFindsForProject(row));
        }
        return ResponseEntity.ok(new ProjectResponse(resource));
    }

    @GetMapping("/{id}/siblings")
    @Operation(summary = "Le projet précédent et le projet suivant dans l'ordre courant",
            description = "Voisins du projet dans la même liste \"projets accessibles\" que "
                    + "GET /projects (mêmes organizationId/search/f.*), jamais un ordre "
                    + "institution-only : un voisin renvoyé ici est toujours ouvrable par "
                    + "l'appelant via GET /projects/{id}. Boucle en fin de liste (le suivant du "
                    + "dernier projet est le premier), sauf quand l'appelant n'a accès à aucun "
                    + "AUTRE projet, auquel cas le champ correspondant est null plutôt que de "
                    + "boucler sur lui-même.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Champ de tri inconnu ou non utilisable pour la navigation"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ProjectSiblingsResponse> getSiblings(
            @PathVariable("id") String id,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String search,
            @Parameter(description = "Champ de tri \"champ:direction\" utilisé pour déterminer l'ordre : "
                    + "id, name, creationTime uniquement (les autres champs de GET /projects ne "
                    + "supportent pas la navigation curseur — voir la doc du endpoint). "
                    + "Défaut : creationTime:asc.")
            @RequestParam(required = false) String sort,
            @Parameter(hidden = true)
            @RequestParam MultiValueMap<String, String> queryParams) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        ProjectListFilter filter = ProjectListFilter.parse(queryParams);
        return ResponseEntity.ok(new ProjectSiblingsResponse(
                projectApiService.findSiblings(caller, id, organizationId, search, sort, filter)));
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
        ProjectResourcePermissions permissions = projectApiService.permissionsFor(caller, row);
        // A just-created project is never already bookmarked.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ProjectResponse(projectResponseMapper.toResource(row, lang, permissions, false)));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Mise à jour partielle d'un projet",
            description = "Champs modifiables : nom, catégorie (type d'opération, "
                    + "`typeConceptId`), date de début, date de fin, localisation précise. "
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
        ProjectResourcePermissions permissions = projectApiService.permissionsFor(caller, row);
        boolean bookmarked = projectApiService.isBookmarked(caller, row, lang);
        return ResponseEntity.ok(new ProjectResponse(projectResponseMapper.toResource(row, lang, permissions, bookmarked)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un projet",
            description = "Uniquement si le projet n'a ni unité d'enregistrement ni sous-projet. "
                    + "Même clé de projet que GET /api/v1/projects/{id}. Droit de suppression requis.")
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


    @Hidden
    @GetMapping(value = "/{id}/geopackage", produces = "application/geopackage+sqlite3")
    public ResponseEntity<Resource> getGeoPackage(@PathVariable String id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }
}
