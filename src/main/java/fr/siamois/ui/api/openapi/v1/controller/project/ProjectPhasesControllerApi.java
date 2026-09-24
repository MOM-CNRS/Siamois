package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.domain.models.phase.Phase;
import org.springframework.util.MultiValueMap;
import fr.siamois.ui.api.openapi.v1.service.FieldQueryService;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.PhaseOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.response.phase.PhaseListResponse;
import fr.siamois.ui.api.openapi.v1.service.PhaseListProjectionService;
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
 * Phases d'un projet ({@code GET /api/v1/projects/{id}/phases}) — pendant réduit de
 * {@link ProjectFindsApi} : recherche + tri seulement, pas de contrat {@code f.<clé>} par colonne
 * ni de projection {@code ?fields=} sur la liste (voir le plan de migration React, lot Phases —
 * même réduction que Mobilier).
 */
@RestController
@RequestMapping("/api/v1/projects/{id}/phases")
@Tag(name = OpenApiTags.PROJECT, description = "Project phases")
@RequiredArgsConstructor
public class ProjectPhasesControllerApi {

    private final ProjectApiService projectApiService;
    private final PhaseOpenApiMapper phaseOpenApiMapper;
    private final PhaseListProjectionService phaseListProjectionService;
    private final ResourceBookmarkService resourceBookmarkService;
    private final FieldQueryService fieldQueryService;

    @GetMapping
    @Operation(summary = "Récupérer la liste paginée des phases d'un projet",
            description = "Clé de projet : identique à GET /api/v1/projects/{id}. Tri : paramètre sort au "
                    + "format « propriété:asc » ou « propriété:desc » (propriétés autorisées : identifier, "
                    + "orderNumber, title, id). Valeur par défaut : orderNumber:asc. Recherche : paramètre "
                    + "search, sur identifier.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination ou de tri invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<PhaseListResponse> listPhases(
            @Parameter(description = "Project id or key", required = true)
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Recherche libre, sur identifier")
            @RequestParam(required = false) String search,
            @Parameter(description = "Tri, ex. identifier:asc ou orderNumber:desc")
            @RequestParam(defaultValue = "orderNumber:asc") String sort,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParams,
            @io.swagger.v3.oas.annotations.Parameter(description = "Projection des champs de formulaire dans answers : "
                    + "\"all\" ou une liste d'ids de champs séparés par des virgules (champs additionnels compris). "
                    + "Absent : pas de clé answers.")
            @RequestParam(required = false) String fields,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        Page<PhaseDTO> page = projectApiService.pagePhasesForProject(caller, id, offset, limit, sort, search,
                fieldQueryService.parse(Phase.class, queryParams, sort, acceptLanguage));

        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);

        boolean canEdit = projectApiService.canEditPhasesForProject(caller, id, lang);
        ProjectResourcePermissions permissions = ProjectResourcePermissions.of(canEdit);

        PhaseListProjectionService.PhaseListProjection projection =
                phaseListProjectionService.build(page.getContent(), fields, lang);

        List<PhaseResource> resources = page.getContent().stream()
                .map(dto -> {
                    PhaseResource resource = phaseOpenApiMapper.toResource(dto, lang, projection.resolvedLabels());
                    if (fields != null) resource.setAnswers(projection.answersFor(dto.getId()));
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
                .body(new PhaseListResponse(resources, meta));
    }
}
