package fr.siamois.ui.api.openapi.v1.controller.place;

import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceAutocompleteItemApi;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceAutocompleteListResponse;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Recherche d'unités spatiales pour l'app mobile (autocomplétion), indépendamment du module « places » historique.
 */
@RestController
@RequestMapping("/api/v1/places")
@Tag(name = OpenApiTags.SPATIAL_UNIT)
@RequiredArgsConstructor
public class PlaceSearchControllerApi {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final ProjectApiService projectApiService;
    private final SpatialUnitService spatialUnitService;
    private final LabelService labelService;

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
    public ResponseEntity<PlaceAutocompleteListResponse> autocomplete(
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

        List<PlaceAutocompleteItemApi> items = page.getContent().stream()
                .map(dto -> toItem(dto, lang))
                .toList();

        ListMeta meta = new ListMeta(page.getTotalElements(), safeLimit, 0L);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(new PlaceAutocompleteListResponse(items, meta));
    }

    private PlaceAutocompleteItemApi toItem(SpatialUnitDTO dto, String lang) {
        ConceptDTO cat = dto.getCategory();
        ResolvedConceptResource concept = null;
        if (cat != null) {
            concept = new ResolvedConceptResource();
            concept.setResourceType("concepts");
            concept.setId(String.valueOf(cat.getId()));
            concept.setExternalUrl(cat.getExternalId());
            concept.setResolvedLabel(labelService.findLabelOf(cat, lang).getLabel());
        }
        return new PlaceAutocompleteItemApi(
                dto.getId(),
                dto.getName(),
                dto.getCode(),
                concept);
    }
}
