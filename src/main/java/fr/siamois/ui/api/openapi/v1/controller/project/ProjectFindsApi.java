package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.response.find.FindListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Recherche de mobiliers (spécimens) d'un projet — autocomplete formulaire, miroir de
 * {@code EntityFormContext.completeSpecimenOptions} (JSF), scopée à l'unité d'action du projet.
 * Anciennement un stub 501 ; implémenté ici en réutilisant {@link FindResource}/{@link FindListResponse}
 * déjà branchés pour {@code GET /api/v1/recording-units/{id}/mobiliers}.
 */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = OpenApiTags.PROJECT)
@RequiredArgsConstructor
public class ProjectFindsApi {

    private final ProjectApiService projectApiService;

    @GetMapping("/{id}/mobiliers")
    @Operation(
            summary = "Rechercher des mobiliers d'un projet",
            description = "Filtre optionnel `q` sur l'identifiant complet (sous-chaîne). Pour l'autocomplete formulaire, "
                    + "résultats limités (pas de pagination serveur, comme l'équivalent JSF)."
    )
    public ResponseEntity<FindListResponse> getFinds(
            @PathVariable String id,
            @Parameter(description = "Filtre optionnel sur l'identifiant complet (sous-chaîne).")
            @RequestParam(required = false) String q) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<FindResource> resources = projectApiService.searchSpecimensForAccessibleProject(caller, id, q);
        ListMeta meta = new ListMeta((long) resources.size(), resources.size(), 0L);
        return ResponseEntity.ok(new FindListResponse(resources, meta));
    }
}
