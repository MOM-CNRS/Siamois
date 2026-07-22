package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import fr.siamois.ui.api.openapi.v1.response.phase.PhaseListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{id}/phases")
@Tag(name = OpenApiTags.PROJECT, description = "Project phases")
public class ProjectPhasesControllerApi {

    private final ProjectApiService projectApiService;

    public ProjectPhasesControllerApi(ProjectApiService projectApiService) {
        this.projectApiService = projectApiService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List project phases",
            description = "Returns the phases of a project accessible to the authenticated user."
    )
    public PhaseListResponse listPhases(
            @Parameter(description = "Project id or key", required = true)
            @PathVariable("id") String id
    ) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<PhaseResource> resources = projectApiService.listPhasesForAccessibleProject(caller, id);
        ListMeta meta = new ListMeta((long) resources.size(), resources.size(), 0L);
        return new PhaseListResponse(resources, meta);
    }
}
