package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiParamIds;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import io.swagger.v3.oas.annotations.media.Schema;
import fr.siamois.ui.api.openapi.v1.request.find.FindCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.find.FindPatchRequest;
import fr.siamois.ui.api.openapi.v1.response.FindResponse;
import fr.siamois.ui.api.openapi.v1.response.FindListResponse;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.response.find.FindFormData;
import fr.siamois.ui.api.openapi.v1.response.find.FindFormResponse;
import fr.siamois.ui.api.openapi.v1.service.FindOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
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


    @GetMapping
    public ResponseEntity<FindListResponse> getAll() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @GetMapping("/{id}")
    public ResponseEntity<FindResponse> getById(@PathVariable Long id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @GetMapping("/creation-form")
    @Operation(
            summary = "Formulaire de création d'un mobilier",
            description = "Bundle formulaire (layout), définition des champs et vocabulaires pour créer un spécimen "
                    + "sur une UE avec un type de mobilier donné. "
                    + "Paramètres : `recordingUnitId` (clé d'UE : recording_unit_id ou full_identifier) et "
                    + "`specimenTypeConceptId` (concept_id du type de mobilier, chaîne numérique). "
                    + "Le type est obligatoire : le formulaire custom dépend du type et de l'institution de l'UE. "
                    + "Les clés de `fieldAnswers` sur POST /mobiliers correspondent aux identifiants de champs retournés ici. "
                    + "Langue des libellés de vocabulaire : en-tête Accept-Language."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "UE sans organisation"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Création non autorisée sur cette UE"),
            @ApiResponse(responseCode = "404", description = "UE ou type de mobilier introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<FindFormResponse> getFindCreateForm(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @RequestParam String recordingUnitId,
            @Parameter(
                    description = "Identifiant du concept de type de mobilier (concept_id, chaîne numérique).",
                    schema = @Schema(type = "string", example = "42")
            )
            @RequestParam String specimenTypeConceptId,
            @Parameter(description = "Langue préférée pour les libellés de vocabulaire (première entrée utilisée).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        FindFormData data = recordingUnitOpenApiService.buildFindCreateForm(
                OpenApiParamIds.requireNonBlank(recordingUnitId, "recordingUnitId"),
                OpenApiParamIds.requireNonBlank(specimenTypeConceptId, "specimenTypeConceptId"),
                caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.ok(new FindFormResponse(data));
    }

    @GetMapping("/{id}/form")
    @Operation(
            summary = "Formulaire d'édition d'un mobilier",
            description = "Bundle formulaire (layout), définition des champs et vocabulaires pour le spécimen identifié par "
                    + "`specimen_id`. Même résolution de formulaire custom que pour une UE : type de mobilier (concept) "
                    + "+ institution de rattachement. Langue des libellés de vocabulaire : en-tête Accept-Language."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Mobilier sans institution de rattachement"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Mobilier introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<FindFormResponse> getFindForm(
            @Parameter(description = "Identifiant numérique du spécimen (specimen_id).", example = "42")
            @PathVariable("id") long id,
            @Parameter(description = "Langue préférée pour les libellés de vocabulaire (première entrée utilisée).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        FindFormData data = recordingUnitOpenApiService.buildFindForm(
                id, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.ok(new FindFormResponse(data));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer un mobilier",
            description = "Crée un spécimen rattaché à une UE (`recordingUnitId`) avec un type (`specimenTypeConceptId`). "
                    + "Valeurs de formulaire optionnelles dans `fieldAnswers` (mêmes clés que sur GET /mobiliers/creation-form "
                    + "ou GET /mobiliers/{id}/form). "
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
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
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
            description = "Met à jour les réponses de formulaire présentes dans `fieldAnswers`. "
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
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
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
}
