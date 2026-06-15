package fr.siamois.ui.api.openapi.v1.controller.recordingunit;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitPatchRequest;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitCreateFormData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitCreateFormResponse;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitMobileDetailData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitMobileDetailResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recording-units")
@Tag(name = OpenApiTags.RECORDING_UNIT)
@RequiredArgsConstructor
public class RecordingUnitsControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;

    @GetMapping("/creation-form")
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

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer une unité d'enregistrement",
            description = "Crée une UE sur un projet (`actionUnitId` : action_unit_id numérique, full_identifier "
                    + "ou identifiant court) avec un type (concept). "
                    + "Les valeurs de formulaire sont passées dans `fieldAnswers` (clés = identifiants de champs, "
                    + "comme sur GET /creation-form). Le formulaire effectif dépend du type d'UE et de l'institution du projet."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créée"),
            @ApiResponse(responseCode = "400", description = "Requête ou formulaire invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Projet ou type introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitMobileDetailResponse> createRecordingUnit(
            @RequestBody RecordingUnitCreateRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        RecordingUnitMobileDetailData data = recordingUnitOpenApiService.createRecordingUnit(
                body, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RecordingUnitMobileDetailResponse(data));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Modifier partiellement une unité d'enregistrement",
            description = "Met à jour les réponses de formulaire présentes dans `fieldAnswers` (fusion partielle). "
                    + "Même clé d'UE que GET /api/v1/recording-units/{id} (identifiant numérique ou full_identifier)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Conflit de révision (modification concurrente)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitMobileDetailResponse> patchRecordingUnit(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id,
            @RequestBody RecordingUnitPatchRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        RecordingUnitMobileDetailData data = recordingUnitOpenApiService.patchRecordingUnit(
                id, body, caller.person(), caller.accessibleInstitutionIds(), lang);
        return ResponseEntity.ok(new RecordingUnitMobileDetailResponse(data));
    }


    @GetMapping("/{id}")
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
                id, caller.person(), caller.accessibleInstitutionIds(), counts, lang);
        return ResponseEntity.ok(new RecordingUnitMobileDetailResponse(data));
    }


    @DeleteMapping("/{id}")
    @Operation(
            summary = "Supprimer une unité d'enregistrement",
            description = "Identifiant numérique obligatoire : clé primaire recording_unit_id. "
                    + "L'UE doit être dans une institution accessible (JWT). Droit d'écriture requis. "
                    + "Refus (409) si l'UE contient des mobiliers, des études ou des unités filles en hiérarchie."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Supprimée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Suppression non autorisée"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Suppression impossible (mobiliers, études ou UE filles)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> deleteByNumericId(
            @Parameter(description = "Identifiant numérique recording_unit_id.", example = "42")
            @PathVariable("id") long id,
            @Parameter(description = "Langue pour le contrôle d'autorisation (UserInfo).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        projectApiService.deleteRecordingUnit(caller, id, acceptLanguage);
        return ResponseEntity.noContent().build();
    }
}
