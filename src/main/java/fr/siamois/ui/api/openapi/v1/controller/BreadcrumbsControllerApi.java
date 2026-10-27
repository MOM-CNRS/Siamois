package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.response.BreadcrumbsResponse;
import fr.siamois.ui.api.openapi.v1.service.EntityBreadcrumbsService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
@Tag(name = OpenApiTags.PROJECT, description = "Fil d'Ariane des fiches d'un projet")
@RequiredArgsConstructor
public class BreadcrumbsControllerApi {

    private final ProjectApiService projectApiService;
    private final EntityBreadcrumbsService entityBreadcrumbsService;

    @GetMapping("/{collection}/{id}/breadcrumbs")
    @Operation(summary = "Fil d'Ariane d'une fiche",
            description = "Tous les chemins du projet jusqu'à la fiche (collections : recording-units, finds, containers, "
                    + "phases, documents, studies). Le premier chemin est celui par défaut ; les parents sont parcourus par "
                    + "identifiant croissant. Pour un mobilier, l'unité d'enregistrement d'appartenance précède la fiche. "
                    + "Un document est placé sous chaque fiche à laquelle il est lié. Le paramètre optionnel via=type:id "
                    + "(clé du registre front) place en premier les chemins passant par la fiche d'où l'on vient.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Collection ou fiche introuvable, ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<BreadcrumbsResponse> getBreadcrumbs(@PathVariable("collection") String collection,
                                                              @PathVariable("id") long id,
                                                              @RequestParam(name = "via", required = false) String via) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        EntityBreadcrumbsService.Kind kind = EntityBreadcrumbsService.Kind.ofCollection(collection);
        if (kind == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection inconnue");
        }
        return ResponseEntity.ok(new BreadcrumbsResponse(
                entityBreadcrumbsService.findBreadcrumbs(kind, id, caller.accessibleInstitutionIds(), via)));
    }
}
