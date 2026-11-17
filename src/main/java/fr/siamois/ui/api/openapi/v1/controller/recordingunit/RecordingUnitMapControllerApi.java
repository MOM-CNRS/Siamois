package fr.siamois.ui.api.openapi.v1.controller.recordingunit;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.request.list.ListWindow;
import fr.siamois.ui.api.openapi.v1.request.list.TreeRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitListFilter;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapFeatureResponse;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;
import fr.siamois.ui.api.openapi.v1.service.FieldQueryService;
import fr.siamois.ui.api.openapi.v1.service.OrganizationListService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.map.EntityMapService;
import fr.siamois.ui.api.openapi.v1.service.map.RecordingUnitMapFeatureSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The map view of the recording-unit lists: the same search, sort and {@code f.<key>} filters as
 * the list endpoints, answering with each unit's geometry in WGS84 instead of a page of rows.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Unité d'enregistrement")
public class RecordingUnitMapControllerApi {

    private static final String MAP_DESCRIPTION = "Mêmes recherche, tri et filtres f.<clé> que la liste. "
            + "Les géométries sont reprojetées en WGS84 (EPSG:4326) par PostGIS depuis leur SRID d'origine, "
            + "sans jamais modifier la donnée stockée. Seules les unités dont la colonne de géométrie n'est pas vide "
            + "sont retenues (meta.total) ; celles dont la géométrie est illisible, sans SRID ou avec un SRID "
            + "inconnu sont examinées (meta.examined) mais absentes de features. Au plus 2000 unités examinées "
            + "(meta.total > meta.examined quand le filtre en compte davantage).";

    private static final String GEOMETRY_PARAM = "Colonne de géométrie (fieldId) : la géométrie de l'UE (défaut) "
            + "ou un champ additionnel de type géométrie, dont la réponse (WKT ou EWKT) est reprojetée de même. "
            + "400 pour un autre champ.";

    private final ProjectApiService projectApiService;
    private final OrganizationListService organizationListService;
    private final FieldQueryService fieldQueryService;
    private final EntityMapService entityMapService;
    private final RecordingUnitMapFeatureSource recordingUnitMapFeatureSource;
    private final RecordingUnitOpenApiService recordingUnitOpenApiService;

    /**
     * The map of an organization's recording-unit list.
     */
    @GetMapping("/api/v1/recording-units/map")
    @Operation(summary = "Unités d'enregistrement d'une organisation, sur une carte", description = MAP_DESCRIPTION)
    @ApiResponse(responseCode = "200", description = "Ok")
    @ApiResponse(responseCode = "400", description = "organizationId absent, tri, filtre ou colonne de géométrie invalides")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    @ApiResponse(responseCode = "404", description = "Organisation introuvable")
    public ResponseEntity<EntityMapResponse> organizationMap(
            @Parameter(description = "Organisation (obligatoire)") @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "creationTime:desc") String sort,
            @Parameter(description = GEOMETRY_PARAM) @RequestParam(required = false) Long geometry,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = organizationListService.requireListOrganization(caller, organizationId);
        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(queryParams);
        var fieldQuery = fieldQueryService.parse(RecordingUnit.class, queryParams, sort, acceptLanguage);
        return ResponseEntity.ok(entityMapService.build(fieldQuery, (offset, mapQuery) ->
                organizationListService.pageRecordingUnits(caller, institution, offset, EntityMapService.PAGE_SIZE,
                        sort, search, filter, mapQuery), recordingUnitMapFeatureSource, geometry));
    }

    /**
     * The map of a project's recording-unit list.
     */
    @GetMapping("/api/v1/projects/{id}/recording-units/map")
    @Operation(summary = "Unités d'enregistrement d'un projet, sur une carte", description = MAP_DESCRIPTION)
    @ApiResponse(responseCode = "200", description = "Ok")
    @ApiResponse(responseCode = "400", description = "Tri, filtre ou colonne de géométrie invalides")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible")
    public ResponseEntity<EntityMapResponse> projectMap(
            @PathVariable("id") String id,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "creationTime:desc") String sort,
            @Parameter(description = GEOMETRY_PARAM) @RequestParam(required = false) Long geometry,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(queryParams);
        var fieldQuery = fieldQueryService.parse(RecordingUnit.class, queryParams, sort, acceptLanguage);
        return ResponseEntity.ok(entityMapService.build(fieldQuery, (offset, mapQuery) ->
                projectApiService.pageRecordingUnitsForProject(caller, id,
                        new ListWindow(offset, EntityMapService.PAGE_SIZE, sort, search), filter, mapQuery,
                        TreeRequest.OFF), recordingUnitMapFeatureSource, geometry));
    }

    /**
     * One recording unit on a map, whether or not a list's filter keeps it.
     */
    @GetMapping("/api/v1/recording-units/{id}/map")
    @Operation(summary = "Une unité d'enregistrement, sur une carte",
            description = "La géométrie de l'unité reprojetée en WGS84 (EPSG:4326), hors de toute liste : pour "
                    + "montrer sur la carte d'une liste la fiche ouverte à côté quand le filtre l'exclut. "
                    + "feature est null quand l'unité n'a pas de géométrie exploitable.")
    @ApiResponse(responseCode = "200", description = "Ok")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "400", description = "Colonne de géométrie invalide")
    @ApiResponse(responseCode = "404", description = "Introuvable ou hors périmètre")
    public ResponseEntity<EntityMapFeatureResponse> unitMap(
            @PathVariable("id") String id,
            @Parameter(description = GEOMETRY_PARAM) @RequestParam(required = false) Long geometry) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(entityMapService.feature(
                recordingUnitOpenApiService.findViewableRecordingUnit(id, caller.person(), caller.accessibleInstitutionIds()),
                recordingUnitMapFeatureSource, geometry));
    }
}
