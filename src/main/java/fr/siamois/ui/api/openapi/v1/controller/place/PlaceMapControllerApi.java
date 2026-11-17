package fr.siamois.ui.api.openapi.v1.controller.place;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.list.ListWindow;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapFeatureResponse;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;
import fr.siamois.ui.api.openapi.v1.service.FieldQueryService;
import fr.siamois.ui.api.openapi.v1.service.PlaceOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.map.EntityMapService;
import fr.siamois.ui.api.openapi.v1.service.map.PlaceMapFeatureSource;
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
 * The map view of the place list: the same search, sort and {@code f.<fieldId>} filters as
 * {@code GET /api/v1/places}, answering with each place's geometry in WGS84 instead of a page of rows.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiTags.SPATIAL_UNIT)
public class PlaceMapControllerApi {

    private static final String GEOMETRY_PARAM = "Colonne de géométrie (fieldId) : seule la géométrie du lieu "
            + "existe (défaut). 400 pour un autre champ.";

    private final ProjectApiService projectApiService;
    private final PlaceOpenApiService placeOpenApiService;
    private final FieldQueryService fieldQueryService;
    private final EntityMapService entityMapService;
    private final PlaceMapFeatureSource placeMapFeatureSource;

    /**
     * The map of an organization's place list.
     */
    @GetMapping("/api/v1/places/map")
    @Operation(summary = "Lieux d'une organisation, sur une carte",
            description = "Mêmes recherche, tri et filtres f.<fieldId> que la liste. Les géométries sont reprojetées "
                    + "en WGS84 (EPSG:4326) par PostGIS depuis leur SRID d'origine, sans jamais modifier la donnée "
                    + "stockée. Seuls les lieux dont la géométrie n'est pas vide sont retenus (meta.total) ; ceux "
                    + "sans SRID ou avec un SRID inconnu sont examinés (meta.examined) mais absents de features. Au plus 2000 lieux "
                    + "examinés (meta.total > meta.examined quand le filtre en compte davantage).")
    @ApiResponse(responseCode = "200", description = "Ok")
    @ApiResponse(responseCode = "400", description = "organizationId absent, tri, filtre ou colonne de géométrie invalides")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    @ApiResponse(responseCode = "404", description = "Organisation introuvable")
    public ResponseEntity<EntityMapResponse> organizationMap(
            @Parameter(description = "Organisation (obligatoire)") @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name:asc") String sort,
            @Parameter(description = GEOMETRY_PARAM) @RequestParam(required = false) Long geometry,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = placeOpenApiService.requireListOrganization(caller, organizationId);
        var fieldQuery = fieldQueryService.parse(SpatialUnit.class, queryParams, sort, acceptLanguage);
        return ResponseEntity.ok(entityMapService.build(fieldQuery, (offset, limit, mapQuery) ->
                placeOpenApiService.pagePlaces(institution,
                        new ListWindow(offset, limit, sort, search), mapQuery),
                placeMapFeatureSource, geometry));
    }

    /**
     * One place on a map, whether or not a list's filter keeps it.
     */
    @GetMapping("/api/v1/places/{id}/map")
    @Operation(summary = "Un lieu, sur une carte",
            description = "La géométrie du lieu reprojetée en WGS84 (EPSG:4326), hors de toute liste : pour montrer "
                    + "sur la carte de la liste la fiche ouverte à côté quand le filtre l'exclut. feature est null "
                    + "quand le lieu n'a pas de géométrie exploitable.")
    @ApiResponse(responseCode = "200", description = "Ok")
    @ApiResponse(responseCode = "400", description = "Colonne de géométrie invalide")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "403", description = "Organisation du lieu hors périmètre")
    @ApiResponse(responseCode = "404", description = "Lieu introuvable")
    public ResponseEntity<EntityMapFeatureResponse> placeMap(
            @PathVariable("id") long id,
            @Parameter(description = GEOMETRY_PARAM) @RequestParam(required = false) Long geometry) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(entityMapService.feature(
                placeOpenApiService.requireAccessible(caller, id), placeMapFeatureSource, geometry));
    }
}
