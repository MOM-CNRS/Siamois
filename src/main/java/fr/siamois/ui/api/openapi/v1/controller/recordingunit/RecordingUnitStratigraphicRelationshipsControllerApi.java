package fr.siamois.ui.api.openapi.v1.controller.recordingunit;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipUpdateRequest;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitStratigraphyWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Écriture des relations stratigraphiques d'une UE. La lecture passe par le champ stratigraphique
 * du détail et par {@code GET /{id}/relation-graph}.
 */
@RestController
@RequestMapping("/api/v1/recording-units")
@Tag(name = OpenApiTags.RECORDING_UNIT)
@RequiredArgsConstructor
public class RecordingUnitStratigraphicRelationshipsControllerApi {

    private static final String ID_DESCRIPTION = "Clé d'UE : identifiant numérique (recording_unit_id)";

    private final ProjectApiService projectApiService;
    private final RecordingUnitStratigraphyWriteService stratigraphyWriteService;

    @PostMapping(value = "/{id}/stratigraphic-relationships", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer une relation stratigraphique",
            description = "Relie l'UE à une autre UE de la même opération : antérieure (plus ancienne), postérieure "
                    + "(plus récente) ou synchrone, avec un type facultatif et un indicateur d'incertitude."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Relation créée"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "409", description = "Relation déjà existante ou cycle stratigraphique"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> add(
            @Parameter(description = ID_DESCRIPTION, schema = @Schema(type = "string", example = "42"))
            @PathVariable("id") String id,
            @RequestBody RecordingUnitStratigraphicRelationshipRequest body) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        stratigraphyWriteService.add(id, body, caller.person(), caller.accessibleInstitutionIds());
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{id}/stratigraphic-relationships/{relatedId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Modifier une relation stratigraphique",
            description = "Remplace le type et l'indicateur d'incertitude de la relation entre l'UE et relatedId. "
                    + "La position (antérieure, postérieure, synchrone) ne change pas : on supprime et on recrée."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Relation modifiée"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE ou relation introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> update(
            @Parameter(description = ID_DESCRIPTION, schema = @Schema(type = "string", example = "42"))
            @PathVariable("id") String id,
            @Parameter(description = "Identifiant numérique recording_unit_id de l'autre UE", example = "88")
            @PathVariable("relatedId") long relatedId,
            @RequestBody RecordingUnitStratigraphicRelationshipUpdateRequest body) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        stratigraphyWriteService.update(id, relatedId, body, caller.person(), caller.accessibleInstitutionIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/stratigraphic-relationships/{relatedId}")
    @Operation(
            summary = "Supprimer une relation stratigraphique",
            description = "Supprime la relation entre l'UE et relatedId, quel que soit son sens."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Relation supprimée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "UE ou relation introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> remove(
            @Parameter(description = ID_DESCRIPTION, schema = @Schema(type = "string", example = "42"))
            @PathVariable("id") String id,
            @Parameter(description = "Identifiant numérique recording_unit_id de l'autre UE", example = "88")
            @PathVariable("relatedId") long relatedId) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        stratigraphyWriteService.remove(id, relatedId, caller.person(), caller.accessibleInstitutionIds());
        return ResponseEntity.noContent().build();
    }
}
