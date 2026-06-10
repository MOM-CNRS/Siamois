package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.SpatialUnitAutocompleteItemApi;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.SpatialUnitAutocompleteListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Recherche d'unités spatiales pour l'app mobile (autocomplétion), indépendamment du module « places » historique.
 */
@RestController
@RequestMapping("/api/v1/places")
@Tag(name = OpenApiTags.SPATIAL_UNIT)
@RequiredArgsConstructor
public class SpatialUnitSearchControllerApi {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final ProjectApiService projectApiService;
    private final SpatialUnitService spatialUnitService;

    @GetMapping("/autocomplete")
    @Operation(
            summary = "Autocomplétion d'unités spatiales par nom",
            description = "Recherche paginée des lieux (spatial_unit) d'une organisation dont le nom contient la chaîne saisie. "
                    + "L'organisation doit être dans le périmètre JWT. Utile pour remplir `spatialContextSpatialUnitIds` sur PATCH /api/v1/projects/{id}."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Paramètres invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<SpatialUnitAutocompleteListResponse> autocomplete(
            @Parameter(description = "Institution propriétaire des lieux (doit être dans le périmètre JWT).", example = "10", required = true)
            @RequestParam("organizationId") long organizationId,
            @Parameter(description = "Sous-chaîne recherchée dans le nom du lieu (insensible à la casse côté requête SQL).", example = "rue")
            @RequestParam("q") String q,
            @Parameter(description = "Nombre max de résultats (1 à 50, défaut 20).")
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());

        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q ne doit pas être vide");
        }
        if (query.length() > 200) {
            query = query.substring(0, 200);
        }
        int safeLimit = limit;
        if (safeLimit < 1) {
            safeLimit = DEFAULT_LIMIT;
        }
        if (safeLimit > MAX_LIMIT) {
            safeLimit = MAX_LIMIT;
        }

        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        Page<SpatialUnitDTO> page = spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                organizationId,
                query,
                null,
                null,
                null,
                lang,
                PageRequest.of(0, safeLimit));

        List<SpatialUnitAutocompleteItemApi> items = page.getContent().stream()
                .map(SpatialUnitSearchControllerApi::toItem)
                .toList();

        ListMeta meta = new ListMeta(page.getTotalElements(), safeLimit, 0L);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(new SpatialUnitAutocompleteListResponse(items, meta));
    }

    private static SpatialUnitAutocompleteItemApi toItem(SpatialUnitDTO dto) {
        ConceptDTO cat = dto.getCategory();
        Long catId = cat != null ? cat.getId() : null;
        String catExt = cat != null ? cat.getExternalId() : null;
        return new SpatialUnitAutocompleteItemApi(
                dto.getId(),
                dto.getName(),
                dto.getCode(),
                catId,
                catExt);
    }
}
