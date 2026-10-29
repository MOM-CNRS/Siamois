package fr.siamois.ui.api.openapi.v1.controller.recordingunit;

import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitRelationGraphResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitRelationRow;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitRelationsResponse;
import fr.siamois.ui.api.openapi.v1.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/recording-units")
@Tag(name = OpenApiTags.RECORDING_UNIT)
@RequiredArgsConstructor
public class RecordingUnitRelationGraphControllerApi {

    private final ProjectApiService projectApiService;
    private final RecordingUnitRelationGraphService relationGraphService;

    private static final Set<String> SORTS = Set.of("kind:asc", "kind:desc", "label:asc", "label:desc");

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
            @Parameter(description = "Graphe complet : jusqu'à 300 UE par zone au lieu de 30, et les relations entre les UE affichées (links)")
            @RequestParam(defaultValue = "false") boolean full,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        RecordingUnitDTO unit = projectApiService.requireViewableRecordingUnit(caller, id);
        return ResponseEntity.ok(relationGraphService.graphOf(unit, ProjectApiService.primaryAcceptLanguage(acceptLanguage), full));
    }

    @GetMapping("/{id}/relations")
    @Operation(
            summary = "Relations d'une unité d'enregistrement, en liste",
            description = "Toutes les relations de l'UE (parents, enfants, antérieures, postérieures, synchrones), "
                    + "à plat et paginées : les zones du graphe sans leur borne. Chaque ligne dit de quel type de relation "
                    + "elle est (`kind`) ; une relation stratigraphique porte son qualificatif. `kind` (répétable) "
                    + "ne garde que ces types, `search` que les UE dont l'identifiant contient ce texte. "
                    + "Même clé d'UE et même périmètre d'accès que GET /api/v1/recording-units/{id}."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Pagination, tri ou type de relation invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "UE introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<RecordingUnitRelationsResponse> getRelations(
            @Parameter(
                    description = "Clé d'UE : identifiant numérique",
                    schema = @Schema(type = "string", example = "2")
            )
            @PathVariable("id") String id,
            @Parameter(description = "Types de relation à garder (répétable) ; tous si absent",
                    schema = @Schema(allowableValues = {"parents", "children", "anterior", "posterior", "synchronous"}))
            @RequestParam(name = "kind", required = false) List<String> kind,
            @Parameter(description = "Ne garde que les UE dont l'identifiant contient ce texte (insensible à la casse)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Ordre : par type de relation puis identifiant, ou par identifiant",
                    schema = @Schema(allowableValues = {"kind:asc", "kind:desc", "label:asc", "label:desc"}))
            @RequestParam(defaultValue = "kind:asc") String sort,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        projectApiService.validatePagedListRequest(offset, limit);
        if (!SORTS.contains(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sort doit valoir kind:asc, kind:desc, label:asc ou label:desc");
        }
        List<RecordingUnitRelationKind> kinds = (kind == null ? List.<String>of() : kind).stream()
                .map(name -> RecordingUnitRelationKind.ofWireName(name).orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "kind doit valoir parents, children, anterior, posterior ou synchronous")))
                .toList();
        ProjectApiCaller caller = projectApiService.requireCaller();
        RecordingUnitDTO unit = projectApiService.requireViewableRecordingUnit(caller, id);
        RelationFieldService.KindedPage page = relationGraphService.relationsOf(unit, kinds, search,
                sort.startsWith("label"), sort.endsWith("asc"), offset, limit, ProjectApiService.primaryAcceptLanguage(acceptLanguage));
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.total()))
                .body(new RecordingUnitRelationsResponse(
                        page.values().stream().map(v -> new RecordingUnitRelationRow(v.kind(), v.value())).toList(),
                        new ListMeta(page.total(), limit, (long) offset)));
    }
}
