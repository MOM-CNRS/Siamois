package fr.siamois.ui.api.openapi.v1.controller.recordingunit;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitListFilter;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitMapResponse;
import fr.siamois.ui.api.openapi.v1.service.FieldQueryService;
import fr.siamois.ui.api.openapi.v1.service.OrganizationListService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitMapService;
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
            + "sans jamais modifier la donnée stockée. Les unités sans géométrie, sans SRID ou avec un SRID "
            + "inconnu sont comptées dans meta.withoutGeometry. Au plus 2000 unités examinées (meta.truncated).";

    private final ProjectApiService projectApiService;
    private final OrganizationListService organizationListService;
    private final FieldQueryService fieldQueryService;
    private final RecordingUnitMapService recordingUnitMapService;

    @GetMapping("/api/v1/recording-units/map")
    @Operation(summary = "Unités d'enregistrement d'une organisation, sur une carte", description = MAP_DESCRIPTION)
    @ApiResponse(responseCode = "200", description = "Ok")
    @ApiResponse(responseCode = "400", description = "organizationId absent, tri ou filtre invalides")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    @ApiResponse(responseCode = "404", description = "Organisation introuvable")
    public ResponseEntity<RecordingUnitMapResponse> organizationMap(
            @Parameter(description = "Organisation (obligatoire)") @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "creationTime:desc") String sort,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = organizationListService.requireListOrganization(caller, organizationId);
        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(queryParams);
        var fieldQuery = fieldQueryService.parse(RecordingUnit.class, queryParams, sort, acceptLanguage);
        return ResponseEntity.ok(recordingUnitMapService.build(offset ->
                organizationListService.pageRecordingUnits(caller, institution, offset, RecordingUnitMapService.PAGE_SIZE,
                        sort, search, filter, fieldQuery)));
    }

    @GetMapping("/api/v1/projects/{id}/recording-units/map")
    @Operation(summary = "Unités d'enregistrement d'un projet, sur une carte", description = MAP_DESCRIPTION)
    @ApiResponse(responseCode = "200", description = "Ok")
    @ApiResponse(responseCode = "400", description = "Tri ou filtre invalides")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible")
    public ResponseEntity<RecordingUnitMapResponse> projectMap(
            @PathVariable("id") String id,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "creationTime:desc") String sort,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        RecordingUnitListFilter filter = RecordingUnitListFilter.parse(queryParams);
        var fieldQuery = fieldQueryService.parse(RecordingUnit.class, queryParams, sort, acceptLanguage);
        return ResponseEntity.ok(recordingUnitMapService.build(offset ->
                projectApiService.pageRecordingUnitsForProject(caller, id, offset, RecordingUnitMapService.PAGE_SIZE,
                        sort, search, filter, fieldQuery)));
    }
}
