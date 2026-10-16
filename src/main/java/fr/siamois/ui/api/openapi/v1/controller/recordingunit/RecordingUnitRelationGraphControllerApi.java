package fr.siamois.ui.api.openapi.v1.controller.recordingunit;

import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitRelationGraphResource;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitRelationGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recording-units")
@Tag(name = OpenApiTags.RECORDING_UNIT)
@RequiredArgsConstructor
public class RecordingUnitRelationGraphControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitRelationGraphService relationGraphService;

    @GetMapping("/{id}/relation-graph")
    @Operation(
            summary = "Graphe des relations d'une unité d'enregistrement",
            description = "Les relations de l'UE regroupées par zone de graphe : parents, enfants, antérieures, "
                    + "synchrones, postérieures, autres. Chaque zone est un aperçu borné (values, total, complete). "
                    + "Même clé d'UE et même périmètre d'accès que GET /api/v1/recording-units/{id}."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitRelationGraphResource> getRelationGraph(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique",
                    schema = @Schema(type = "string", example = "2")
            )
            @PathVariable("id") String id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        RecordingUnitDTO unit = projectApiService.requireViewableRecordingUnit(caller, id);
        return ResponseEntity.ok(relationGraphService.graphOf(unit, ProjectApiService.primaryAcceptLanguage(acceptLanguage)));
    }
}
