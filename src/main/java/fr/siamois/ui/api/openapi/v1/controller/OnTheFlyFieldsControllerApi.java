package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.OnTheFlyMeasurementRequest;
import fr.siamois.ui.api.openapi.v1.resource.form.OnTheFlyFieldsResponse;
import fr.siamois.ui.api.openapi.v1.service.OnTheFlyFieldOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
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

import java.util.Map;

/**
 * "Ajouter une mesure": the fields a user adds on the fly to one record's fiche, whatever the kind of
 * record ({@code recording-units}, {@code finds}, {@code phases}, {@code containers}, {@code documents},
 * {@code studies}).
 */
@RestController
@RequestMapping("/api/v1/{resourceType}/{id}/on-the-fly-fields")
@Tag(name = OpenApiTags.PROJECT, description = "Champs ajoutés à la volée à la fiche d'un enregistrement")
@RequiredArgsConstructor
public class OnTheFlyFieldsControllerApi {

    private static final Map<String, ConfigurableTable> TABLES = Map.of(
            "recording-units", ConfigurableTable.UE,
            "finds", ConfigurableTable.MOBILIER,
            "phases", ConfigurableTable.PHASE,
            "containers", ConfigurableTable.CONTENANT,
            "documents", ConfigurableTable.DOCUMENT,
            "studies", ConfigurableTable.STUDY);

    private final ProjectApiService projectApiService;
    private final OnTheFlyFieldOpenApiService service;

    @GetMapping
    @Operation(summary = "Champs ajoutés à la volée",
            description = "Les champs ajoutés à la fiche de cet enregistrement et le panneau de chacun.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Introuvable ou hors périmètre")
    })
    public ResponseEntity<OnTheFlyFieldsResponse> list(
            @Parameter(description = "recording-units, finds, phases, containers, documents ou studies")
            @PathVariable("resourceType") String resourceType,
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(service.list(table(resourceType), id, caller.person(),
                caller.accessibleInstitutionIds(), ProjectApiService.primaryAcceptLanguage(acceptLanguage)));
    }

    @GetMapping("/measurement-concepts")
    @Operation(summary = "Concepts proposés pour une mesure",
            description = "kind=TYPE (ce qui est mesuré) ou NATURE ; q filtre par libellé.")
    public ResponseEntity<java.util.List<OnTheFlyFieldOpenApiService.Option>> measurementConcepts(
            @PathVariable("resourceType") String resourceType,
            @PathVariable("id") long id,
            @RequestParam(value = "kind", defaultValue = "TYPE") String kind,
            @RequestParam(value = "q", required = false) String q,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(service.measurementConcepts(table(resourceType), id, kind, q, caller.person(),
                caller.accessibleInstitutionIds(), ProjectApiService.primaryAcceptLanguage(acceptLanguage)));
    }

    @GetMapping("/existing-measurements")
    @Operation(summary = "Mesures déjà utilisées sur la même table",
            description = "Les mesures ajoutées à d'autres enregistrements de la même table dans le projet, à reprendre.")
    public ResponseEntity<java.util.List<OnTheFlyFieldOpenApiService.ExistingMeasure>> existingMeasurements(
            @PathVariable("resourceType") String resourceType,
            @PathVariable("id") long id,
            @RequestParam(value = "q", required = false) String q,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(service.existingMeasurements(table(resourceType), id, q, caller.person(),
                caller.accessibleInstitutionIds(), ProjectApiService.primaryAcceptLanguage(acceptLanguage)));
    }

    @GetMapping("/measurement-units")
    @Operation(summary = "Unités proposées pour une mesure")
    public ResponseEntity<java.util.List<OnTheFlyFieldOpenApiService.UnitOption>> measurementUnits(
            @PathVariable("resourceType") String resourceType,
            @PathVariable("id") long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(service.measurementUnits(table(resourceType), id, caller.person(),
                caller.accessibleInstitutionIds(), ProjectApiService.primaryAcceptLanguage(acceptLanguage)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Ajouter une mesure",
            description = "Ajoute à la fiche une mesure propre à cet enregistrement, dans le panneau demandé. "
                    + "Droit d'édition de l'enregistrement requis.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok : les champs de l'enregistrement après l'ajout"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Introuvable ou hors périmètre")
    })
    public ResponseEntity<OnTheFlyFieldsResponse> add(
            @PathVariable("resourceType") String resourceType,
            @PathVariable("id") long id,
            @RequestBody OnTheFlyMeasurementRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(service.addMeasurement(table(resourceType), id, body, caller.person(),
                caller.accessibleInstitutionIds(), ProjectApiService.primaryAcceptLanguage(acceptLanguage)));
    }

    @DeleteMapping("/{fieldId}")
    @Operation(summary = "Retirer un champ ajouté à la volée",
            description = "Détache le champ de la fiche de cet enregistrement. Droit d'édition requis.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok : les champs de l'enregistrement après le retrait"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Interdit"),
            @ApiResponse(responseCode = "404", description = "Introuvable ou hors périmètre")
    })
    public ResponseEntity<OnTheFlyFieldsResponse> remove(
            @PathVariable("resourceType") String resourceType,
            @PathVariable("id") long id,
            @PathVariable("fieldId") long fieldId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(service.remove(table(resourceType), id, fieldId, caller.person(),
                caller.accessibleInstitutionIds(), ProjectApiService.primaryAcceptLanguage(acceptLanguage)));
    }

    private static ConfigurableTable table(String resourceType) {
        ConfigurableTable table = TABLES.get(resourceType);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Type d'enregistrement inconnu");
        }
        return table;
    }
}
