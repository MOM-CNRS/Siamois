package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitAlreadyExistsException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.GeoApiService;
import fr.siamois.domain.services.GeoPlatService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.FullAddress;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.ConceptMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    public static final String SOURCE_INSEE = "INSEE";
    public static final String SOURCE_GEOPLAT = "GEOPLAT";
    public static final Set<String> EXTERNAL_SOURCES = Set.of(SOURCE_INSEE, SOURCE_GEOPLAT);

    static final int MAX_SUGGESTIONS = 12;
    private static final int MAX_NAME_LENGTH = 200;
    private static final String CONCEPT_THESAURUS = "th252";
    private static final String COMMUNE_CONCEPT = "4287976";
    private static final String ADDRESS_CONCEPT = "4288314";

    private final ProjectApiService projectApiService;
    private final SpatialUnitService spatialUnitService;
    private final InstitutionService institutionService;
    private final ProfilePermissionService profilePermissionService;
    private final GeoApiService geoApiService;
    private final GeoPlatService geoPlatService;
    private final ConceptRepository conceptRepository;
    private final ConceptMapper conceptMapper;
    private final LabelService labelService;

    /** Keeps the known sources of a comma-separated list, in upper case; the rest is ignored. */
    public static List<String> parseSources(String sources) {
        if (sources == null || sources.isBlank()) {
            return List.of();
        }
        List<String> parsed = new ArrayList<>();
        for (String raw : sources.split(",")) {
            String source = raw.trim().toUpperCase(Locale.ROOT);
            if (EXTERNAL_SOURCES.contains(source) && !parsed.contains(source)) {
                parsed.add(source);
            }
        }
        return parsed;
    }

    public List<PlaceSuggestionItemApi> suggest(ProjectApiCaller caller, long organizationId, String query,
                                                List<String> sources, int limit, String lang) {
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());

        List<PlaceSuggestionItemApi> result = new ArrayList<>();
        Set<String> knownCodes = new HashSet<>();
        Page<SpatialUnitDTO> page = spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                organizationId, query, null, null, null, lang, PageRequest.of(0, limit, Sort.by("name")));
        for (SpatialUnitDTO dto : page.getContent()) {
            if (dto.getCode() != null) {
                knownCodes.add(dto.getCode());
            }
            result.add(new PlaceSuggestionItemApi(dto.getId(), dto.getName(), dto.getCode(), SOURCE_INTERNAL,
                    resolveConcept(dto.getCategory(), lang), null));
        }

        for (String source : sources) {
            if (result.size() >= MAX_SUGGESTIONS) {
                break;
            }
            for (PlaceSuggestionItemApi item : externalSuggestions(source, query, lang)) {
                if (result.size() >= MAX_SUGGESTIONS) {
                    break;
                }
                // A commune already in the organization is offered once, as the place it is.
                if (item.code() == null || SOURCE_GEOPLAT.equals(source) || !knownCodes.contains(item.code())) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private List<PlaceSuggestionItemApi> externalSuggestions(String source, String query, String lang) {
        try {
            if (SOURCE_INSEE.equals(source)) {
                List<PlaceSuggestionDTO> communes = geoApiService.fetchCommunes(query);
                return communes.stream()
                        .map(c -> new PlaceSuggestionItemApi(null, c.getName(), c.getCode(), SOURCE_INSEE,
                                resolveConcept(c.getCategory(), lang), null))
                        .toList();
            }
            if (SOURCE_GEOPLAT.equals(source) && query != null && query.trim().length() >= 3) {
                ResolvedConceptResource concept = resolveConcept(categoryOf(SOURCE_GEOPLAT), lang);
                return geoPlatService.search(query).stream()
                        .map(a -> new PlaceSuggestionItemApi(null, a.getLabel(), null, SOURCE_GEOPLAT, concept, a))
                        .toList();
            }
        } catch (RuntimeException e) {
            // An external service being down must not take the organization's own places with it.
            log.warn("Place suggestions from {} unavailable: {}", source, e.getMessage());
        }
        return List.of();
    }

    @Transactional
    public PlaceCreatedResponse.PlaceCreatedItem fromSuggestion(ProjectApiCaller caller,
                                                                PlaceFromSuggestionRequest request, String lang) {
        if (request == null || request.getOrganizationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId est obligatoire");
        }
        String source = request.getSource() == null ? "" : request.getSource().trim().toUpperCase(Locale.ROOT);
        if (!EXTERNAL_SOURCES.contains(source)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source inconnue : " + request.getSource());
        }
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

        ConceptDTO category = categoryOf(source);
        String code = SOURCE_INSEE.equals(source) && request.getCode() != null && !request.getCode().isBlank()
                ? request.getCode().trim() : null;

        var existing = spatialUnitService.findExistingForSuggestion(institution.getId(), name, code, category.getId());
        if (existing.isPresent()) {
            SpatialUnitDTO found = existing.get();
            return new PlaceCreatedResponse.PlaceCreatedItem(found.getId(), found.getName(), found.getCode(), found.getPlaceNumber());
        }

        SpatialUnitDTO toSave = new SpatialUnitDTO();
        toSave.setName(name);
        toSave.setCategory(category);
        toSave.setCode(code);
        if (SOURCE_GEOPLAT.equals(source)) {
            toSave.setAddress(request.getAddress() != null ? request.getAddress() : addressOnly(name));
        }
        try {
            SpatialUnitDTO saved = spatialUnitService.save(userInfo, toSave);
            return new PlaceCreatedResponse.PlaceCreatedItem(saved.getId(), saved.getName(), saved.getCode(), saved.getPlaceNumber());
        } catch (SpatialUnitAlreadyExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
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

    private static FullAddress addressOnly(String label) {
        FullAddress address = new FullAddress();
        address.setLabel(label);
        return address;
    }

    private ConceptDTO categoryOf(String source) {
        String externalId = SOURCE_INSEE.equals(source) ? COMMUNE_CONCEPT : ADDRESS_CONCEPT;
        return conceptMapper.convert(conceptRepository.findConceptByExternalIdIgnoreCase(CONCEPT_THESAURUS, externalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Concept de lieu introuvable : " + externalId)));
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
