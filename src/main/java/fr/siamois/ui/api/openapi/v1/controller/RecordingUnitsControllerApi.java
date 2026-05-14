package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.resource.document.ProjectDocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.response.FindListResponse;
import fr.siamois.ui.api.openapi.v1.response.RecordingUnitListResponse;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitChildrenData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitChildrenResponse;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitCreateFormData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitCreateFormResponse;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitDocumentsData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitDocumentsResponse;
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
import io.swagger.v3.oas.annotations.hidden.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recording-units")
@Tag(name = OpenApiTags.RECORDING_UNIT, description = "Endpoints des unités d'enregistrement")
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

    @Hidden
    @GetMapping
    public ResponseEntity<RecordingUnitListResponse> getAll() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
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
                id, caller.person(), caller.accessibleInstitutionIds(), counts, lang);
        return ResponseEntity.ok(new RecordingUnitMobileDetailResponse(data));
    }

    @GetMapping("/{id}/documents")
    @Operation(
            summary = "Documents rattachés à une unité d'enregistrement",
            description = "Liste des documents liés à l'UE via recording_unit_document. "
                    + "Même clé d'UE que GET /api/v1/recording-units/{id} (identifiant numérique ou full_identifier)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<RecordingUnitDocumentsResponse> getDocuments(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        List<ProjectDocumentResource> documents = projectApiService.listDocumentsForAccessibleRecordingUnit(caller, id);
        return ResponseEntity.ok(new RecordingUnitDocumentsResponse(new RecordingUnitDocumentsData(documents)));
    }

    @GetMapping("/{id}/relations")
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

    @GetMapping("/{id}/children")
    @Operation(
            summary = "Unités d'enregistrement enfants d'une UE",
            description = "Liste des UE filles directes liées via recording_unit_hierarchy (fk_parent_id = l'UE cible). "
                    + "Même clé d'UE et périmètre d'accès que GET /api/v1/recording-units/{id} et /relations."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<RecordingUnitChildrenResponse> getChildren(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        RecordingUnitChildrenData data = recordingUnitOpenApiService.buildRecordingUnitChildren(
                id, caller.accessibleInstitutionIds());
        return ResponseEntity.ok(new RecordingUnitChildrenResponse(data));
    }

    @GetMapping("/{id}/finds")
    @Operation(
            summary = "Liste des mobiliers d'une unité d'enregistrement",
            description = "Spécimens liés à l'UE (table specimen, fk_recording_unit_id). "
                    + "Pagination : offset, limit (max 200, offset multiple de limit). "
                    + "Tri : creationTime, id ou fullIdentifier (ex. creationTime:desc). "
                    + "Même clé d'UE que GET /api/v1/recording-units/{id}. "
                    + "La langue pour les libellés de type suit Accept-Language."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Pagination invalide ou UE sans institution"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<FindListResponse> getFinds(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Tri, ex. fullIdentifier:asc ou creationTime:desc")
            @RequestParam(defaultValue = "creationTime:desc") String sort,
            @Parameter(description = "Langue pour le classement des libellés de type (requête SQL).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        Page<FindResource> page = projectApiService.pageFindsForAccessibleRecordingUnit(
                caller, id, offset, limit, sort, acceptLanguage);
        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(new FindListResponse(page.getContent(), meta));
    }
}
