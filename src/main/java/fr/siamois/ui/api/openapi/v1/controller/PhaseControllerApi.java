package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.response.SiblingsResponse;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.phase.PhaseCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.phase.PhasePatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import fr.siamois.ui.api.openapi.v1.response.phase.PhaseResponse;
import fr.siamois.ui.api.openapi.v1.service.PhaseOpenApiService;
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
@RequestMapping("/api/v1/phases")
@Tag(name = OpenApiTags.PROJECT, description = "Endpoints des phases")
@RequiredArgsConstructor
public class PhaseControllerApi {

    private final ProjectApiService projectApiService;
    private final PhaseOpenApiService phaseOpenApiService;

    @GetMapping("/{id}/siblings")
    @Operation(summary = "Phase précédente et suivante",
            description = "Voisins dans le même projet, par ordre de création. Boucle en fin de liste (le suivant du "
                    + "dernier est le premier) ; null seulement s'il n'y a aucun autre élément.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<SiblingsResponse> getSiblings(@PathVariable("id") long id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new SiblingsResponse(
                phaseOpenApiService.findSiblings(id, caller.person(), caller.accessibleInstitutionIds())));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Une phase via son identifiant",
            description = "Valeurs de tous les champs formulaire (Phase.DETAILS_FORM), indexées par fieldId."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Phase introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<PhaseResponse> getById(
            @Parameter(description = "Identifiant numérique de la phase (phase_id).", example = "42")
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        PhaseResource resource = phaseOpenApiService.getPhaseById(id, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.ok(new PhaseResponse(resource));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer une phase",
            description = "Crée une phase dans un projet. Droit requis : gestionnaire d'institution, "
                    + "d'organisation ou de projet (édition des phases)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créée"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Projet ou type introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<PhaseResponse> createPhase(
            @RequestBody PhaseCreateRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        PhaseResource resource = phaseOpenApiService.createPhase(
                body, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PhaseResponse(resource));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Modifier partiellement une phase",
            description = "Met à jour les réponses de formulaire présentes dans `answers` (fusion partielle)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Phase introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<PhaseResponse> patchPhase(
            @Parameter(description = "Identifiant numérique de la phase (phase_id).", example = "42")
            @PathVariable("id") long id,
            @RequestBody PhasePatchRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        PhaseResource resource = phaseOpenApiService.patchPhase(
                id, body, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.ok(new PhaseResponse(resource));
    }
}
