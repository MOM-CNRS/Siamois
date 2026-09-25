package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.domain.models.specimen.Specimen;
import org.springframework.util.MultiValueMap;
import fr.siamois.ui.api.openapi.v1.service.FieldQueryService;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.response.find.FindListResponse;
import fr.siamois.ui.api.openapi.v1.service.FindListProjectionService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.ResourceBookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Mobiliers d'un projet ({@code GET /api/v1/projects/{id}/mobiliers}) — même contrat que
 * {@link ProjectContainersControllerApi} : recherche, tri et filtres {@code f.<id de champ>},
 * projection {@code ?fields=} dans {@code answers}.
 */
@RestController
@RequestMapping("/api/v1/projects/{id}/mobiliers")
@Tag(name = OpenApiTags.PROJECT)
@RequiredArgsConstructor
public class ProjectFindsApi {

    private final ProjectApiService projectApiService;
    private final FindOpenApiMapper findOpenApiMapper;
    private final ResourceBookmarkService resourceBookmarkService;
    private final FieldQueryService fieldQueryService;
    private final FindListProjectionService findListProjectionService;

    @GetMapping
    @Operation(summary = "Récupérer la liste paginée des mobiliers d'un projet",
            description = "Clé de projet : identique à GET /api/v1/projects/{id}. Tri : paramètre sort au "
                    + "format « propriété:asc » ou « propriété:desc » (propriétés autorisées : fullIdentifier, "
                    + "collectionDate, id). Valeur par défaut : fullIdentifier:asc. Recherche : paramètre "
                    + "search, sur fullIdentifier.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination ou de tri invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<FindListResponse> getFinds(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Recherche libre, sur fullIdentifier")
            @RequestParam(required = false) String search,
            @Parameter(description = "Tri, ex. fullIdentifier:asc ou collectionDate:desc")
            @RequestParam(defaultValue = "fullIdentifier:asc") String sort,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParams,
            @io.swagger.v3.oas.annotations.Parameter(description = "Projection des champs de formulaire dans answers : "
                    + "\"all\" ou une liste d'ids de champs séparés par des virgules (champs additionnels compris). "
                    + "Absent : pas de clé answers.")
            @RequestParam(required = false) String fields,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        Page<SpecimenDTO> page = projectApiService.pageFindsForProject(caller, id, offset, limit, sort, search,
                fieldQueryService.parse(Specimen.class, queryParams, sort, acceptLanguage));

        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        // One boolean for the whole page (every row shares this project), same pattern as the
        // recording-units list.
        boolean canEdit = projectApiService.canEditFindsForProject(caller, id, lang);
        ProjectResourcePermissions permissions = ProjectResourcePermissions.of(canEdit)
                .withValidate(projectApiService.canValidateForProject(caller, id, lang));

        FindListProjectionService.FindListProjection projection = findListProjectionService.build(page.getContent(), fields, lang);

        List<FindResource> resources = page.getContent().stream()
                .map(dto -> {
                    FindResource resource = findOpenApiMapper.toResource(dto);
                    if (fields != null) resource.setAnswers(projection.answersFor(dto.getId()));
                    resource.setPermissions(permissions);
                    if (dto.getId() != null) {
                        resource.setResourceUri("/specimen/" + dto.getId());
                    }
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
                .body(new FindListResponse(resources, meta));
    }

}
