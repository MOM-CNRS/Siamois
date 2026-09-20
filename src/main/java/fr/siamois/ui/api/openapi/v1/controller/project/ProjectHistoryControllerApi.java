package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectHistoryEntryResource;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectHistoryListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Revision history for the fiche header's "last modified" / history section (plan §4/§5) — not a
 * separate tab. Thin wrapper over {@link fr.siamois.domain.services.history.HistoryAuditService},
 * generic for any entity type already, not Project-specific.
 */
@RestController
@RequestMapping("/api/v1/projects/{id}/history")
@Tag(name = OpenApiTags.PROJECT, description = "Project revision history")
public class ProjectHistoryControllerApi {

    private final ProjectApiService projectApiService;

    public ProjectHistoryControllerApi(ProjectApiService projectApiService) {
        this.projectApiService = projectApiService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Historique des révisions d'un projet",
            description = "Le plus récent en premier.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ProjectHistoryListResponse listHistory(
            @Parameter(description = "Project id or key", required = true)
            @PathVariable("id") String id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<ProjectHistoryEntryResource> resources = projectApiService.listHistoryForAccessibleProject(caller, id);
        ListMeta meta = new ListMeta((long) resources.size(), resources.size(), 0L);
        return new ProjectHistoryListResponse(resources, meta);
    }
}
