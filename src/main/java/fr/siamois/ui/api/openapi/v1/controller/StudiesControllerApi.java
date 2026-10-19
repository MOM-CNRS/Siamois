package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.response.SiblingsResponse;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.study.StudyCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.study.StudyPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.study.StudyResource;
import fr.siamois.ui.api.openapi.v1.response.study.StudyResponse;
import fr.siamois.ui.api.openapi.v1.service.StudyOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
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
@RequestMapping("/api/v1/studies")
@Tag(name = OpenApiTags.PROJECT, description = "Endpoints des études")
@RequiredArgsConstructor
public class StudiesControllerApi {

    private final ProjectApiService projectApiService;
    private final StudyOpenApiService studyOpenApiService;

    @GetMapping("/{id}/siblings")
    @Operation(summary = "Étude précédente et suivante",
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
                studyOpenApiService.findSiblings(id, caller.person(), caller.accessibleInstitutionIds())));
    }

    @ValuesLimit.Param(defaultValue = ValuesLimit.DETAIL_DEFAULT)
    @GetMapping("/{id}")
    @Operation(
            summary = "Une étude via son identifiant",
            description = "Valeurs de tous les champs formulaire (StudyForm), indexées par fieldId."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Étude introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<StudyResponse> getById(
            @Parameter(description = "Identifiant numérique de l'étude (study_id).", example = "42")
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        StudyResource resource = studyOpenApiService.getStudyById(id, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.ok(new StudyResponse(resource));
    }

    @ValuesLimit.Param(defaultValue = ValuesLimit.DETAIL_DEFAULT)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer unee étude",
            description = "Crée une étude dans un projet. Droit requis : gestionnaire d'institution, "
                    + "d'organisation ou de projet (édition des études)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Projet ou type introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<StudyResponse> createStudy(
            @RequestBody StudyCreateRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        StudyResource resource = studyOpenApiService.createStudy(
                body, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.status(HttpStatus.CREATED).body(new StudyResponse(resource));
    }

    @ValuesLimit.Param(defaultValue = ValuesLimit.DETAIL_DEFAULT)
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Modifier partiellement une étude",
            description = "Met à jour les réponses de formulaire présentes dans `answers` (fusion partielle)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Étude introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<StudyResponse> patchStudy(
            @Parameter(description = "Identifiant numérique de l'étude (study_id).", example = "42")
            @PathVariable("id") long id,
            @RequestBody StudyPatchRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        StudyResource resource = studyOpenApiService.patchStudy(
                id, body, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.ok(new StudyResponse(resource));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Supprimer l'étude",
            description = "Supprime l'étude. Refus (409) si elle a encore des relations avec d'autres entités "
                    + "(à retirer d'abord). Droit d'édition requis sur le projet."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Supprimé"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Étude introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Suppression impossible (relations existantes)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> deleteStudy(
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        studyOpenApiService.deleteStudy(id, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.noContent().build();
    }
}
