package fr.siamois.ui.api.openapi.v1.controller.recordingunit;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitHierarchyLinkRequest;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitChildrenData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitChildrenResponse;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitRelationsData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitRelationsResponse;
import fr.siamois.ui.api.openapi.v1.service.DocumentWriteOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/recording-units")
@Tag(name = OpenApiTags.RECORDING_UNIT)
@RequiredArgsConstructor
public class RecordingUnitChildrenControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    private final DocumentWriteOpenApiService documentWriteOpenApiService;



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
                    description = "Clé d'UE : identifiant numérique",
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
}
