package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerCreateRequest;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import fr.siamois.ui.api.openapi.v1.response.container.ContainerListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contenants (caisses, boîtes...) d'un projet : recherche (autocomplete formulaire) et création minimale
 * inline, miroir de {@code EntityFormContext.getContainerOptions}/{@code saveNewContainerFromField} (JSF),
 * jusqu'ici sans équivalent REST.
 */
@RestController
@RequestMapping("/api/v1/projects/{id}/containers")
@Tag(name = OpenApiTags.PROJECT, description = "Project containers")
@RequiredArgsConstructor
public class ProjectContainersControllerApi {

    private final ProjectApiService projectApiService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Rechercher des contenants",
            description = "Filtre optionnel `q` sur l'identifiant (sous-chaîne). Pour l'autocomplete formulaire."
    )
    public ContainerListResponse searchContainers(
            @Parameter(description = "Project id or key", required = true)
            @PathVariable("id") String id,
            @Parameter(description = "Filtre optionnel sur l'identifiant (sous-chaîne).")
            @RequestParam(required = false) String q
    ) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<ContainerResource> resources = projectApiService.searchContainersForAccessibleProject(caller, id, q);
        ListMeta meta = new ListMeta((long) resources.size(), resources.size(), 0L);
        return new ContainerListResponse(resources, meta);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer un contenant",
            description = "Création minimale (unité d'action résolue depuis l'URL, type/parent optionnels) — "
                    + "pour la création inline depuis un champ formulaire. Droit d'écriture PROJECT_EDIT_CONTAINERS requis."
    )
    public ResponseEntity<ContainerResource> createContainer(
            @Parameter(description = "Project id or key", required = true)
            @PathVariable("id") String id,
            @RequestBody(required = false) ContainerCreateRequest request
    ) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        ContainerCreateRequest body = request != null ? request : new ContainerCreateRequest();
        ContainerResource resource = projectApiService.createContainerForAccessibleProject(
                caller, id, body.getTypeConceptId(), body.getParentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(resource);
    }
}
