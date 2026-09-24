package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import fr.siamois.ui.api.openapi.v1.response.container.ContainerResponse;
import fr.siamois.ui.api.openapi.v1.service.ContainerOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/containers")
@Tag(name = OpenApiTags.PROJECT, description = "Endpoints des contenants")
@RequiredArgsConstructor
public class ContainerControllerApi {

    private final ProjectApiService projectApiService;
    private final ContainerOpenApiService containerOpenApiService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Un contenant via son identifiant",
            description = "Valeurs de tous les champs formulaire (Container.DETAILS_FORM), indexées par fieldId."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Contenant introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ContainerResponse> getById(
            @Parameter(description = "Identifiant numérique du contenant (container_id).", example = "42")
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        ContainerResource resource = containerOpenApiService.getContainerById(id, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.ok(new ContainerResponse(resource));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer un contenant",
            description = "Crée un contenant dans un projet. Droit requis : gestionnaire d'institution, "
                    + "d'organisation ou de projet (édition des contenants)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Projet ou type introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ContainerResponse> createContainer(
            @RequestBody ContainerCreateRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        ContainerResource resource = containerOpenApiService.createContainer(
                body, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ContainerResponse(resource));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Modifier partiellement un contenant",
            description = "Met à jour les réponses de formulaire présentes dans `answers` (fusion partielle)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Contenant introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<ContainerResponse> patchContainer(
            @Parameter(description = "Identifiant numérique du contenant (container_id).", example = "42")
            @PathVariable("id") long id,
            @RequestBody ContainerPatchRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        ContainerResource resource = containerOpenApiService.patchContainer(
                id, body, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.ok(new ContainerResponse(resource));
    }
}
