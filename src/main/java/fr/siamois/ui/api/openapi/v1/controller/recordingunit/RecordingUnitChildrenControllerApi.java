package fr.siamois.ui.api.openapi.v1.controller.recordingunit;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitHierarchyLinkRequest;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitListResponse;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/recording-units")
@Tag(name = OpenApiTags.RECORDING_UNIT)
@RequiredArgsConstructor
public class RecordingUnitChildrenControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    

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
    public ResponseEntity<RecordingUnitListResponse> getChildren(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique",
                    schema = @Schema(type = "string", example = "2")
            )
            @PathVariable("id") String id) {
        // todo : add pagination and meta counts
        ProjectApiCaller caller = projectApiService.requireCaller();
        List<RecordingUnitResource> data = recordingUnitOpenApiService.buildRecordingUnitChildren(
                id, caller.accessibleInstitutionIds());
        return ResponseEntity.ok(new RecordingUnitListResponse(data, null));
    }

    @PostMapping(value = "/{id}/children", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Lier une unité d'enregistrement existante comme enfant",
            description = "Ajoute une relation hiérarchique directe (recording_unit_hierarchy) entre l'UE cible (parent) "
                    + "et une UE existante (enfant). Les deux UE doivent appartenir au même projet."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Relation créée"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Relation déjà existante ou cycle hiérarchique"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> addExistingChild(
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
        recordingUnitOpenApiService.addExistingChild(
                id, body.getRelatedRecordingUnitId(), caller.person(), caller.accessibleInstitutionIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/children/{relatedId}")
    @Operation(
            summary = "Supprimer un enfant existant",
            description = "Supprime la relation hiérarchique directe entre l'UE cible (parent) "
                    + "et l'UE enfant identifiée par relatedId (recording_unit_id)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Relation supprimée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Relation inexistante"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> removeExistingChild(
            @Parameter(
                    description = "Clé d'UE parente : identifiant numérique (recording_unit_id) ou full_identifier.",
                    schema = @Schema(type = "string", example = "INST-PROJ-UE42")
            )
            @PathVariable("id") String id,
            @Parameter(description = "Identifiant numérique recording_unit_id de l'UE enfant à délier.", example = "88")
            @PathVariable("relatedId") long relatedId) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        recordingUnitOpenApiService.removeExistingChild(
                id, relatedId, caller.person(), caller.accessibleInstitutionIds());
        return ResponseEntity.noContent().build();
    }
}
