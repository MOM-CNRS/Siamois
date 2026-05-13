package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.response.FindListResponse;
import fr.siamois.ui.api.openapi.v1.response.RecordingUnitListResponse;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitCreateFormData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitCreateFormResponse;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitMobileDetailData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitMobileDetailResponse;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitRelationsData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitRelationsResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recording-units")
@Tag(name = "Unité d'enregistrement", description = "Endpoints des unités d'enregistrement")
public class RecordingUnitsControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;

    public RecordingUnitsControllerApi(ProjectApiService projectApiService,
                                       RecordingUnitOpenApiService recordingUnitOpenApiService) {
        this.projectApiService = projectApiService;
        this.recordingUnitOpenApiService = recordingUnitOpenApiService;
    }

    @Operation(
            summary = "Formulaire de création d'une unité d'enregistrement",
            description = "Bundle formulaire (layout), définition des champs et vocabulaires pour un type d'UE donné "
                    + "(concept) dans le contexte d'une organisation. "
                    + "Paramètres : `organizationId` (institution dans le périmètre JWT) et `recordingUnitTypeConceptId` "
                    + "(identifiant du concept de type d'UE). "
                    + "La langue des libellés de vocabulaire suit l'en-tête Accept-Language."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation ou type d'UE introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/creation-form")
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<RecordingUnitCreateFormResponse> getRecordingUnitCreateForm(
            @Parameter(description = "Institution (doit être dans le périmètre JWT).", example = "10")
            @RequestParam long organizationId,
            @Parameter(description = "Identifiant du concept définissant le type d'UE (concept_id).", example = "42")
            @RequestParam long recordingUnitTypeConceptId,
            @Parameter(description = "Langue préférée pour les libellés de vocabulaire (première entrée utilisée).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        RecordingUnitCreateFormData data = recordingUnitOpenApiService.buildRecordingUnitCreateForm(
                organizationId, recordingUnitTypeConceptId, caller.person(), lang);
        return ResponseEntity.ok(new RecordingUnitCreateFormResponse(data));
    }

    @Operation(summary = "La liste des unités d'enregistrement")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping
    public ResponseEntity<RecordingUnitListResponse> getAll() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Operation(
            summary = "Une unité d'enregistrement via son identifiant",
            description = "Clé d'URL : identifiant numérique (recording_unit_id) ou identifiant métier complet "
                    + "(full_identifier) dans une de vos organisations. "
                    + "Inclut le formulaire effectif pour le type d'UE, les définitions de champs et les vocabulaires "
                    + "(champs à liste contrôlée) pour usage hors ligne. "
                    + "La langue des libellés de vocabulaire suit l'en-tête Accept-Language. "
                    + "Réservé aux institutions accessibles par l'utilisateur authentifié (JWT)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}")
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<RecordingUnitMobileDetailResponse> getById(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id,
            @Parameter(
                    description = "Compteurs optionnels à inclure (ex. specimen).",
                    schema = @Schema(type = "array", allowableValues = {"specimen"}),
                    in = ParameterIn.QUERY
            )
            @RequestParam(required = false) List<String> counts,
            @Parameter(description = "Langue préférée pour les libellés de vocabulaire (première entrée utilisée).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        RecordingUnitMobileDetailData data = recordingUnitOpenApiService.buildMobileDetail(
                id,
                caller.person(),
                caller.accessibleInstitutionIds(),
                counts,
                lang);

        return ResponseEntity.ok(new RecordingUnitMobileDetailResponse(data));
    }

    @Operation(
            summary = "Relations d'une unité d'enregistrement",
            description = "Stratigraphie (relations unit1/unit2) et hiérarchie (parents et enfants via recording_unit_hierarchy). "
                    + "Même périmètre d'accès que le détail UE (institutions du JWT)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/relations")
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<RecordingUnitRelationsResponse> getRelations(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        RecordingUnitRelationsData data = recordingUnitOpenApiService.buildRecordingUnitRelations(
                id, caller.accessibleInstitutionIds());
        return ResponseEntity.ok(new RecordingUnitRelationsResponse(data));
    }

    @Operation(summary = "La liste des mobiliers d'une UE")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/finds")
    @Tag(name = "Mobilier")
    public ResponseEntity<FindListResponse> getFinds(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

}
