package fr.siamois.ui.api.openapi.v1.controller.place;

import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceAutocompleteItemApi;
import fr.siamois.ui.api.openapi.v1.request.place.PlaceFromSuggestionRequest;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceAutocompleteListResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceSuggestionItemApi;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceSuggestionListResponse;
import fr.siamois.ui.api.openapi.v1.service.PlaceSuggestionApiService;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final PlaceSuggestionApiService placeSuggestionApiService;

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
            @Parameter(description = "Sous-chaîne recherchée dans le nom du lieu (insensible à la casse côté requête SQL). "
                    + "Vide ou absente : la première page des lieux de l'organisation, par nom — ce qu'un sélecteur montre à l'ouverture.", example = "rue")
            @RequestParam(value = "q", required = false, defaultValue = "") String q,
            @Parameter(description = "Nombre max de résultats (1 à 50, défaut 20).")
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());

        String query = q == null ? "" : q.trim();
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
                PageRequest.of(0, safeLimit, Sort.by("name")));

        List<PlaceAutocompleteItemApi> items = page.getContent().stream()
                .map(dto -> toItem(dto, lang))
                .toList();

        ListMeta meta = new ListMeta(page.getTotalElements(), safeLimit, 0L);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(new PlaceAutocompleteListResponse(items, meta));
    }

    @GetMapping("/suggestions")
    @Operation(
            summary = "Suggestions de lieux, de l'organisation et de sources externes",
            description = "Les lieux de l'organisation dont le nom contient `q`, puis les suggestions des sources demandées "
                    + "(INSEE : communes ; GEOPLAT : adresses de la GéoPlateforme). Une source inconnue est ignorée. "
                    + "Une suggestion externe (id null) se transforme en lieu par POST /api/v1/places/from-suggestion."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<PlaceSuggestionListResponse> suggestions(
            @Parameter(description = "Institution propriétaire des lieux (doit être dans le périmètre JWT).", required = true)
            @RequestParam("organizationId") long organizationId,
            @Parameter(description = "Texte recherché (3 caractères au moins pour les sources externes).")
            @RequestParam(value = "q", required = false, defaultValue = "") String q,
            @Parameter(description = "Sources externes, séparées par des virgules : INSEE, GEOPLAT.", example = "INSEE")
            @RequestParam(value = "sources", required = false) String sources,
            @Parameter(description = "Nombre max de lieux de l'organisation (1 à 50, défaut 20).")
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String query = q == null ? "" : q.trim();
        if (query.length() > 200) {
            query = query.substring(0, 200);
        }
        int safeLimit = limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        List<PlaceSuggestionItemApi> items = placeSuggestionApiService.suggest(
                caller, organizationId, query, PlaceSuggestionApiService.parseSources(sources), safeLimit, lang);
        return ResponseEntity.ok(new PlaceSuggestionListResponse(items, new ListMeta((long) items.size(), safeLimit, 0L)));
    }

    @PostMapping(value = "/from-suggestion", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer le lieu d'une suggestion externe",
            description = "Crée dans l'organisation le lieu d'une suggestion INSEE ou GEOPLAT, ou renvoie celui qui existe déjà "
                    + "(même code et même type, sinon même nom) : l'appel est idempotent. "
                    + "Droit requis : édition du projet `projectId`, ou création de projets dans l'organisation si `projectId` est absent."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lieu déjà existant ou créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Création non autorisée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<PlaceCreatedResponse> fromSuggestion(
            @RequestBody PlaceFromSuggestionRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new PlaceCreatedResponse(placeSuggestionApiService.fromSuggestion(caller, request, lang)));
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
