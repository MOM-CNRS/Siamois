package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiParamIds;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import io.swagger.v3.oas.annotations.media.Schema;
import fr.siamois.ui.api.openapi.v1.request.find.FindCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.find.FindPatchRequest;
import fr.siamois.ui.api.openapi.v1.response.FindResponse;
import fr.siamois.ui.api.openapi.v1.response.FindListResponse;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.response.find.FindMobilierFormResponse;
import fr.siamois.ui.api.openapi.v1.service.FindOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import io.swagger.v3.oas.annotations.Hidden;
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
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/api/v1/mobiliers")
@Tag(name = OpenApiTags.MOBILIER, description = "Endpoints des mobiliers (spécimens)")
@RequiredArgsConstructor
public class MobilierControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    private final FindOpenApiService findOpenApiService;


    @Hidden
    @GetMapping
    public ResponseEntity<FindListResponse> getAll() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Formulaire d'un mobilier",
            description = "Retourne le bundle formulaire (layout) et les champs avec leurs valeurs actuelles "
                    + "pour le spécimen identifié par sa clé : `specimen_id` (chaîne numérique) ou `full_identifier`. "
                    + "Les vocabulaires ne sont pas inclus : utiliser GET /api/v1/vocabularies. "
                    + "Langue des libellés de champs : en-tête Accept-Language."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Mobilier sans organisation ou sans type"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Mobilier introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<FindMobilierFormResponse> getById(
            @Parameter(
                    description = "Clé du mobilier : identifiant numérique (specimen_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42-M1")
            )
            @PathVariable("id") String id,
            @Parameter(description = "Langue préférée pour les libellés de champs (première entrée utilisée).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(new FindMobilierFormResponse(
                recordingUnitOpenApiService.buildFindMobilierForm(
                        OpenApiParamIds.requireNonBlank(id, "id"),
                        caller.person(), caller.accessibleInstitutionIds(), lang)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer un mobilier",
            description = "Crée un spécimen rattaché à une UE (`recordingUnitId`) avec un type (`specimenTypeConceptId`). "
                    + "Valeurs de formulaire optionnelles dans `fieldAnswers` (clés = identifiants custom_field). "
                    + "Droit d'écriture requis sur l'UE parente."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE ou type introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<FindResponse> createFind(
            @RequestBody FindCreateRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        FindResource resource = findOpenApiService.createFind(
                body, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.status(HttpStatus.CREATED).body(new FindResponse(resource));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Modifier partiellement un mobilier",
            description = "Met à jour les réponses de formulaire présentes dans `fieldAnswers` "
                    + "(mêmes clés que sur GET /mobiliers/{id}). "
                    + "Identifiant numérique du spécimen (`specimen_id`)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Mobilier introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<FindResponse> patchFind(
            @Parameter(description = "Identifiant numérique du spécimen (specimen_id).", example = "42")
            @PathVariable("id") long id,
            @RequestBody FindPatchRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        FindResource resource = findOpenApiService.patchFind(
                id, body, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.ok(new FindResponse(resource));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Supprimer un mobilier",
            description = "Supprime le spécimen identifié par `specimen_id` (identifiant numérique). "
                    + "Droit d'écriture requis sur l'UE parente. "
                    + "Refus (409) si le mobilier a des mouvements ou appartient à un groupe de spécimens. "
                    + "Les documents liés sont détachés mais non supprimés."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Supprimé"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Mobilier introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Suppression impossible (mouvements ou groupes)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> deleteFind(
            @Parameter(description = "Identifiant numérique du spécimen (specimen_id).", example = "42")
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        findOpenApiService.deleteFind(id, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.noContent().build();
    }
}
