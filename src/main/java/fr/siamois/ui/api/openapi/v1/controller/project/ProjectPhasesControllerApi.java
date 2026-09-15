package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.request.phase.PhaseCreateRequest;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import fr.siamois.ui.api.openapi.v1.response.phase.PhaseListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            description = "Returns the phases of a project accessible to the authenticated user. "
                    + "Optional `q` filters by title/identifier substring (case-insensitive) — for a phase-picker autocomplete."
    )
    public PhaseListResponse listPhases(
            @Parameter(description = "Project id or key", required = true)
            @PathVariable("id") String id,
            @Parameter(description = "Filtre optionnel titre/identifiant (sous-chaîne, insensible à la casse).")
            @RequestParam(required = false) String q
    ) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<PhaseResource> resources = projectApiService.listPhasesForAccessibleProject(caller, id, q);
        ListMeta meta = new ListMeta((long) resources.size(), resources.size(), 0L);
        return new PhaseListResponse(resources, meta);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer une phase",
            description = "Création minimale (titre + unité d'action résolue depuis l'URL) — pour la création "
                    + "inline depuis un champ formulaire. Droit d'écriture PROJECT_EDIT_PHASES requis."
    )
    public ResponseEntity<PhaseResource> createPhase(
            @Parameter(description = "Project id or key", required = true)
            @PathVariable("id") String id,
            @RequestBody PhaseCreateRequest request
    ) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        PhaseResource resource = projectApiService.createPhaseForAccessibleProject(
                caller, id, request.getTitle(), request.getOrderNumber(), request.getTypeConceptId());
        return ResponseEntity.status(HttpStatus.CREATED).body(resource);
    }
}
