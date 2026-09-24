package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.dto.FieldQuery;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.ui.api.openapi.v1.service.FieldQueryService;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitListFilter;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.ResourceBookmarkService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitListProjectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{id}/recording-units")
@Tag(name = OpenApiTags.PROJECT)
@RequiredArgsConstructor
public class ProjectRecordingUnitsControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitResponseMapper recordingUnitResourceMapper;
    private final RecordingUnitListProjectionService recordingUnitListProjectionService;
    private final ResourceBookmarkService resourceBookmarkService;
    private final FieldQueryService fieldQueryService;


    @GetMapping
    @Operation(summary = "Récupérer la liste paginée des unités d'enregistrement d'un projet",
            description = "Clé de projet : identique à GET /api/v1/projects/{id} (id numérique, fullIdentifier, identifiant court). "
                    + "Tri : paramètre sort au format « propriété:asc » ou « propriété:desc » "
                    + "(propriétés autorisées : creationTime, id, identifier, fullIdentifier, openingDate, closingDate, "
                    + "plus les colonnes de RecordingUnitTableColumnDefaults et leurs tris synthétiques — "
                    + "specimenCount, relationshipCount, parentsCount, childrenCount, *Label). "
                    + "Valeur par défaut : creationTime:desc. Recherche : paramètre search, sur fullIdentifier. "
                    + "Filtres : paramètres f.<clé> (voir RecordingUnitListFilter) ; f.actionUnit est refusé, "
                    + "le chemin scope déjà sur ce projet. Projection : paramètre fields (\"all\", \"default\", "
                    + "ou une liste d'ids séparés par des virgules) ; absent, la réponse n'a pas de clé answers. "
                    + "Chaque élément inclut notamment : identifiant, type, nombre de relations stratigraphiques, "
                    + "nombre de mobiliers, dates, lieu (référence place), couleur de matrice, auteur et contributeurs.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination, tri ou filtre invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitListResponse> getList(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Recherche libre, sur fullIdentifier")
            @RequestParam(required = false) String search,
            @Parameter(description = "Tri, ex. fullIdentifier:asc ou creationTime:desc")
            @RequestParam(defaultValue = "creationTime:desc") String sort,
            @Parameter(description = "Projection des champs de formulaire dans answers : \"all\", \"default\" "
                    + "(colonnes visibles par défaut de la liste), ou une liste d'ids de champs séparés par "
                    + "des virgules. Absent : pas de clé answers, la liste reste au coût d'avant.")
            @RequestParam(required = false) String fields,
            @Parameter(hidden = true)
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        // f.<key>[.from|.to] — see RecordingUnitListFilter for the full contract. Parsed from the
        // raw query params, same as ProjectListFilter on the project list.
        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(queryParams);
        FieldQuery fieldQuery = fieldQueryService.parse(RecordingUnit.class, queryParams, sort, acceptLanguage);
        Page<RecordingUnitDTO> page = projectApiService.pageRecordingUnitsForProject(
                caller, id, offset, limit, sort, search, filter);

        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        // One boolean for the whole page (every row shares this project), not per-row like the
        // project list's own permissionsFor (which spans several action units at once).
        boolean canEdit = projectApiService.canEditRecordingUnitsForProject(caller, id, lang);
        ProjectResourcePermissions permissions = ProjectResourcePermissions.of(canEdit);

        // Labels et projection answers : un seul lot pour la page, jamais par ligne.
        RecordingUnitListProjectionService.RecordingUnitListProjection projection =
                recordingUnitListProjectionService.build(page.getContent(), fields, lang);

        List<RecordingUnitResource> resources = page.getContent().stream()
                .map(dto -> {
                    RecordingUnitResource resource = recordingUnitResourceMapper.convert(dto);
                    resource.setAnswers(projection.answersFor(dto.getId()));
                    resource.setPermissions(permissions);
                    return resource;
                })
                .toList();
        // Every row shares this project, hence one institution — one bookmark query for the page.
        if (!page.isEmpty()) {
            resourceBookmarkService.markBookmarked(caller.person(), page.getContent().get(0).getCreatedByInstitution(), resources, lang);
        }

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(new RecordingUnitListResponse(resources, meta));
    }

}
