package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.ContainerOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.response.container.ContainerListResponse;
import fr.siamois.ui.api.openapi.v1.service.ContainerListProjectionService;
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
 * Contenants d'un projet ({@code GET /api/v1/projects/{id}/containers}) — pendant réduit de
 * {@link ProjectPhasesControllerApi} : recherche + tri seulement (même réduction que
 * Phases/Mobilier).
 */
@RestController
@RequestMapping("/api/v1/projects/{id}/containers")
@Tag(name = OpenApiTags.PROJECT, description = "Project containers")
@RequiredArgsConstructor
public class ProjectContainersControllerApi {

    private final ProjectApiService projectApiService;
    private final ContainerOpenApiMapper containerOpenApiMapper;
    private final ContainerListProjectionService containerListProjectionService;
    private final ResourceBookmarkService resourceBookmarkService;

    @GetMapping
    @Operation(summary = "Récupérer la liste paginée des contenants d'un projet",
            description = "Clé de projet : identique à GET /api/v1/projects/{id}. Tri : paramètre sort au "
                    + "format « propriété:asc » ou « propriété:desc » (propriétés autorisées : identifier, "
                    + "id). Valeur par défaut : identifier:asc. Recherche : paramètre search, sur identifier.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination ou de tri invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ContainerListResponse> listContainers(
            @Parameter(description = "Project id or key", required = true)
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Recherche libre, sur identifier")
            @RequestParam(required = false) String search,
            @Parameter(description = "Tri, ex. identifier:asc ou id:desc")
            @RequestParam(defaultValue = "identifier:asc") String sort,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        Page<ContainerDTO> page = projectApiService.pageContainersForProject(caller, id, offset, limit, sort, search);

        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        boolean canEdit = projectApiService.canEditContainersForProject(caller, id, lang);
        ProjectResourcePermissions permissions = ProjectResourcePermissions.of(canEdit);

        ContainerListProjectionService.ContainerListProjection projection =
                containerListProjectionService.build(page.getContent(), null, lang);

        List<ContainerResource> resources = page.getContent().stream()
                .map(dto -> {
                    ContainerResource resource = containerOpenApiMapper.toResource(dto, lang, projection.resolvedLabels());
                    resource.setPermissions(permissions);
                    return resource;
                })
                .toList();
        // Every row shares this project, hence one institution — one bookmark query for the page.
        if (!page.isEmpty()) {
            resourceBookmarkService.markBookmarked(caller.person(), page.getContent().get(0).getActionUnit().getCreatedByInstitution(), resources, lang);
        }

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(new ContainerListResponse(resources, meta));
    }
}
