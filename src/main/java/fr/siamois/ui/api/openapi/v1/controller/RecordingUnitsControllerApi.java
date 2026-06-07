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
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitHierarchyLinkRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitPatchRequest;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitRelationsData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitRelationsResponse;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentResourceResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recording-units")
@Tag(name = OpenApiTags.RECORDING_UNIT)
@RequiredArgsConstructor
public class RecordingUnitsControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    private final DocumentWriteOpenApiService documentWriteOpenApiService;

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Créer un document rattaché à une unité d'enregistrement",
            description = "Crée un document et le lie à l'UE (recording_unit_document). "
                    + "Champs multipart : title (obligatoire), file (obligatoire), description, "
                    + "natureConceptId, scaleConceptId, formatConceptId. "
                    + "Modification, suppression et téléchargement : /api/v1/documents/{id}."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE ou concept introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<DocumentResourceResponse> createRecordingUnitDocument(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "natureConceptId", required = false) Long natureConceptId,
            @RequestParam(value = "scaleConceptId", required = false) Long scaleConceptId,
            @RequestParam(value = "formatConceptId", required = false) Long formatConceptId,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        var resource = documentWriteOpenApiService.createForRecordingUnit(
                caller, id, title, description, natureConceptId, scaleConceptId, formatConceptId, file, lang);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DocumentResourceResponse(resource));
    }

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

    @Hidden
    @GetMapping
    public ResponseEntity<RecordingUnitListResponse> getAll() {
        // Réponse directe 501 : en MockMvc standalone, ResponseStatusException peut être
        // enveloppée et traitée comme 500 par le handler générique.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
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

    @PostMapping(value = "/{id}/children", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Lier une unité d'enregistrement existante comme enfant",
            description = "Ajoute une relation hiérarchique directe (recording_unit_hierarchy) entre l'UE cible (parent) "
                    + "et une UE existante (enfant). Les deux UE doivent appartenir au même projet. "
                    + "Retourne l'état complet des relations (stratigraphie, parents, enfants)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Relation créée"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Relation déjà existante ou cycle hiérarchique"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitRelationsResponse> addExistingChild(
            @Parameter(
                    description = "Clé d'UE parente : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id,
            @RequestBody RecordingUnitHierarchyLinkRequest body) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        if (body == null || body.getRelatedRecordingUnitId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "relatedRecordingUnitId est obligatoire");
        }
        RecordingUnitRelationsData data = recordingUnitOpenApiService.addExistingChild(
                id, body.getRelatedRecordingUnitId(), caller.person(), caller.accessibleInstitutionIds());
        return ResponseEntity.ok(new RecordingUnitRelationsResponse(data));
    }

    @PostMapping(value = "/{id}/parents", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Lier une unité d'enregistrement existante comme parent",
            description = "Ajoute une relation hiérarchique directe (recording_unit_hierarchy) entre une UE existante (parent) "
                    + "et l'UE cible (enfant). Les deux UE doivent appartenir au même projet. "
                    + "Retourne l'état complet des relations (stratigraphie, parents, enfants)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Relation créée"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Relation déjà existante ou cycle hiérarchique"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitRelationsResponse> addExistingParent(
            @Parameter(
                    description = "Clé d'UE enfant : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id,
            @RequestBody RecordingUnitHierarchyLinkRequest body) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        if (body == null || body.getRelatedRecordingUnitId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "relatedRecordingUnitId est obligatoire");
        }
        RecordingUnitRelationsData data = recordingUnitOpenApiService.addExistingParent(
                id, body.getRelatedRecordingUnitId(), caller.person(), caller.accessibleInstitutionIds());
        return ResponseEntity.ok(new RecordingUnitRelationsResponse(data));
    }

    @DeleteMapping("/{id}/children/{relatedId}")
    @Operation(
            summary = "Supprimer un enfant existant",
            description = "Supprime la relation hiérarchique directe entre l'UE cible (parent) et l'UE enfant identifiée "
                    + "par relatedId (recording_unit_id). Retourne l'état complet des relations."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Relation supprimée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Relation inexistante"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitRelationsResponse> removeExistingChild(
            @Parameter(
                    description = "Clé d'UE parente : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id,
            @Parameter(description = "Identifiant numérique recording_unit_id de l'UE enfant à délier.", example = "99")
            @PathVariable("relatedId") long relatedId) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        RecordingUnitRelationsData data = recordingUnitOpenApiService.removeExistingChild(
                id, relatedId, caller.person(), caller.accessibleInstitutionIds());
        return ResponseEntity.ok(new RecordingUnitRelationsResponse(data));
    }

    @DeleteMapping("/{id}/parents/{relatedId}")
    @Operation(
            summary = "Supprimer un parent existant",
            description = "Supprime la relation hiérarchique directe entre l'UE parent identifiée par relatedId "
                    + "(recording_unit_id) et l'UE cible (enfant). Retourne l'état complet des relations."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Relation supprimée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Relation inexistante"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitRelationsResponse> removeExistingParent(
            @Parameter(
                    description = "Clé d'UE enfant : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id,
            @Parameter(description = "Identifiant numérique recording_unit_id de l'UE parente à délier.", example = "88")
            @PathVariable("relatedId") long relatedId) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        RecordingUnitRelationsData data = recordingUnitOpenApiService.removeExistingParent(
                id, relatedId, caller.person(), caller.accessibleInstitutionIds());
        return ResponseEntity.ok(new RecordingUnitRelationsResponse(data));
    }

    @GetMapping("/{id}/mobiliers")
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
