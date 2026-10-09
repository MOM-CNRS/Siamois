package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.form.rules.PlaceSourceSpec;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.placesource.ExternalPlace;
import fr.siamois.domain.services.placesource.PlaceSourceConfigResolver;
import fr.siamois.domain.services.placesource.PlaceSourceProvider;
import fr.siamois.domain.services.placesource.PlaceSourceRegistry;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.request.place.PlaceFromSuggestionRequest;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceSuggestionItemApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Place suggestions for the spatial-unit fields: the places of the organization first, then the external sources the
 * field is configured with (INSEE communes, GéoPlateforme addresses). Choosing an external suggestion creates the
 * place, once per organization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceSuggestionApiService {

    public static final String SOURCE_INTERNAL = "SIAMOIS";

    private static final int MAX_NAME_LENGTH = 200;

    private final ProjectApiService projectApiService;
    private final SpatialUnitService spatialUnitService;
    private final InstitutionService institutionService;
    private final ProfilePermissionService profilePermissionService;
    private final PlaceSourceRegistry sourceRegistry;
    private final PlaceSourceConfigResolver configResolver;
    private final LabelService labelService;

    /**
     * The suggestions, and the sources a parameter of which has no value (the field it reads is empty): searched
     * without that filter (the default) or left out when configured to skip — the client invites to fill that field.
     */
    public record Suggestions(List<PlaceSuggestionItemApi> items, List<String> unnarrowedSources) {
    }

    /** The query parameters of a source, and whether every one it is bound to got a value. */
    private record SourceParams(Map<String, String> values, boolean complete) {
    }

    /**
     * What a suggestion request asks for: the places of {@code organizationId} matching {@code query}, then the
     * suggestions of the sources {@code fieldId} is configured with. {@code deps} are the places picked in the
     * fields a source's parameters read (by field id).
     */
    public record SuggestionRequest(long organizationId, long fieldId, @Nullable String projectId, @Nullable String typeId,
                                    String query, Map<Long, Long> deps, int limit, String lang) {
    }

    public Suggestions suggest(ProjectApiCaller caller, SuggestionRequest request) {
        projectApiService.assertOrganizationInCallerScope(request.organizationId(), caller.accessibleInstitutionIds());

        List<PlaceSuggestionItemApi> result = new ArrayList<>(ownPlaces(request));
        Set<String> knownCodes = result.stream().map(PlaceSuggestionItemApi::code).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<String> unnarrowed = new ArrayList<>();
        Map<Long, SpatialUnitDTO> depPlaces = new LinkedHashMap<>();
        for (PlaceSourceSpec spec : configResolver.forField(request.fieldId(), request.projectId(), request.typeId())) {
            result.addAll(fromSource(spec, request, depPlaces, knownCodes, unnarrowed));
        }
        return new Suggestions(result, unnarrowed);
    }

    private List<PlaceSuggestionItemApi> ownPlaces(SuggestionRequest request) {
        Page<SpatialUnitDTO> page = spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                request.organizationId(), request.query(), null, null, null, request.lang(),
                PageRequest.of(0, request.limit(), Sort.by("name")));
        return page.getContent().stream()
                .map(dto -> new PlaceSuggestionItemApi(dto.getId(), dto.getName(), dto.getCode(), SOURCE_INTERNAL,
                        resolveConcept(dto.getCategory(), request.lang()), null))
                .toList();
    }

    /**
     * What one configured source suggests. A source with a parameter left without value is reported in
     * {@code unnarrowed}, and not queried at all when it is configured to skip.
     */
    private List<PlaceSuggestionItemApi> fromSource(PlaceSourceSpec spec, SuggestionRequest request,
                                                    Map<Long, SpatialUnitDTO> depPlaces, Set<String> knownCodes,
                                                    List<String> unnarrowed) {
        Optional<PlaceSourceProvider> found = sourceRegistry.find(spec.source());
        if (found.isEmpty()) {
            return List.of();
        }
        PlaceSourceProvider provider = found.get();
        SourceParams params = paramsOf(spec, request.deps(), depPlaces, request.organizationId());
        if (!params.complete()) {
            unnarrowed.add(spec.source());
            if (spec.onMissing() == PlaceSourceSpec.OnMissing.SKIP) {
                return List.of();
            }
        }
        // A commune already in the organization is offered once, as the place it is.
        return externalSuggestions(provider, request.query(), params.values(), request.lang()).stream()
                .filter(item -> !provider.identifiedByCode() || item.code() == null || !knownCodes.contains(item.code()))
                .toList();
    }

    /**
     * The query parameters of {@code spec}, read from the places picked in the fields they are bound
     * to; one with no value is left out, which broadens the search.
     */
    private SourceParams paramsOf(PlaceSourceSpec spec, Map<Long, Long> deps,
                                                   Map<Long, SpatialUnitDTO> cache, long organizationId) {
        Map<String, String> params = new LinkedHashMap<>();
        boolean complete = true;
        for (Map.Entry<String, PlaceSourceSpec.ParamBinding> entry : spec.params().entrySet()) {
            PlaceSourceSpec.ParamBinding binding = entry.getValue();
            String value = valueOf(binding, deps, cache, organizationId);
            if (value != null) {
                params.put(entry.getKey(), value);
            } else {
                complete = false;
            }
        }
        return new SourceParams(params, complete);
    }

    private String valueOf(PlaceSourceSpec.ParamBinding binding, Map<Long, Long> deps,
                           Map<Long, SpatialUnitDTO> cache, long organizationId) {
        Long placeId = deps.get(binding.fromField());
        if (placeId == null) return null;
        SpatialUnitDTO place = cache.containsKey(placeId) ? cache.get(placeId) : loadPlace(placeId, cache);
        // A place of another organization is not a dependency this caller can feed a query with.
        if (place == null || place.getCreatedByInstitution() == null
                || !Long.valueOf(organizationId).equals(place.getCreatedByInstitution().getId())) {
            return null;
        }
        String value = switch (binding.attribute()) {
            case CODE -> place.getCode();
            case NAME -> place.getName();
            case POSTCODE -> place.getAddress() == null ? null : place.getAddress().getPostcode();
        };
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SpatialUnitDTO loadPlace(long placeId, Map<Long, SpatialUnitDTO> cache) {
        SpatialUnitDTO place = null;
        try {
            place = spatialUnitService.findById(placeId);
        } catch (SpatialUnitNotFoundException e) {
            // A place that is gone feeds nothing: the parameter has no value.
            log.debug("Place {} a source parameter reads no longer exists", placeId);
        }
        cache.put(placeId, place);
        return place;
    }

    private List<PlaceSuggestionItemApi> externalSuggestions(PlaceSourceProvider provider, String query,
                                                             Map<String, String> params, String lang) {
        try {
            ConceptDTO category = provider.category();
            ResolvedConceptResource concept = resolveConcept(category, lang);
            return provider.search(query, params).stream()
                    .map(place -> new PlaceSuggestionItemApi(null, place.name(), place.code(), provider.id(), concept, place.address()))
                    .toList();
        } catch (RuntimeException e) {
            // An external service being down must not take the organization's own places with it.
            log.warn("Place suggestions from {} unavailable: {}", provider.id(), e.getMessage());
            return List.of();
        }
    }

    @Transactional
    public PlaceCreatedResponse.PlaceCreatedItem fromSuggestion(ProjectApiCaller caller,
                                                                PlaceFromSuggestionRequest request, String lang) {
        if (request == null || request.getOrganizationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId est obligatoire");
        }
        String sourceId = request.getSource() == null ? "" : request.getSource().trim().toUpperCase(Locale.ROOT);
        PlaceSourceProvider provider = sourceRegistry.find(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "source inconnue : " + request.getSource()));
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name est obligatoire");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        projectApiService.assertOrganizationInCallerScope(request.getOrganizationId(), caller.accessibleInstitutionIds());
        InstitutionDTO institution = institutionService.findById(request.getOrganizationId());
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable");
        }
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        requireMayCreate(caller, userInfo, request.getProjectId());

        ConceptDTO category = provider.category();
        ExternalPlace picked = new ExternalPlace(name, blankToNull(request.getCode()), request.getAddress());
        SpatialUnitDTO draft = provider.draftOf(picked);
        String code = draft.getCode();

        var existing = spatialUnitService.findExistingForSuggestion(institution.getId(), draft.getName(), code, category.getId());
        if (existing.isPresent()) {
            SpatialUnitDTO found = existing.get();
            return new PlaceCreatedResponse.PlaceCreatedItem(found.getId(), found.getName(), found.getCode(), found.getPlaceNumber());
        }
        try {
            SpatialUnitDTO saved = spatialUnitService.save(userInfo, draft);
            return new PlaceCreatedResponse.PlaceCreatedItem(saved.getId(), saved.getName(), saved.getCode(), saved.getPlaceNumber());
        } catch (SpatialUnitAlreadyExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Editing the project the place is for, or creating projects in the organization; managing places also does. */
    private void requireMayCreate(ProjectApiCaller caller, UserInfo userInfo, String projectId) {
        boolean allowed = profilePermissionService.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_PLACES);
        if (!allowed && projectId != null && !projectId.isBlank()) {
            ActionUnitDTO project = projectApiService.requireAccessibleProject(caller, projectId).actionUnit();
            allowed = profilePermissionService.hasActionUnitWritePermission(userInfo, project);
        } else if (!allowed) {
            allowed = profilePermissionService.hasActionUnitCreatePermission(userInfo);
        }
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de lieu non autorisée");
        }
    }

    private ResolvedConceptResource resolveConcept(ConceptDTO category, String lang) {
        if (category == null) {
            return null;
        }
        ResolvedConceptResource concept = new ResolvedConceptResource();
        concept.setResourceType("concepts");
        concept.setId(String.valueOf(category.getId()));
        concept.setExternalUrl(category.getExternalId());
        concept.setResolvedLabel(labelService.findLabelOf(category, lang).getLabel());
        return concept;
    }
}
