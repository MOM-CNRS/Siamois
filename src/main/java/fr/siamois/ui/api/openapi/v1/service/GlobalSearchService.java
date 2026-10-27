package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.misc.GlobalSearchRow;
import fr.siamois.infrastructure.database.repositories.misc.SearchRepository;
import fr.siamois.ui.api.openapi.v1.response.search.GlobalSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Recherche globale de la barre du haut : quelques correspondances par type d'entité plus leur total,
 * limitées aux projets que l'appelant peut voir, et au projet courant si le client le demande.
 */
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    /** Types d'entité (clés du registre React) dans l'ordre d'affichage des sections. */
    static final List<String> TYPES = List.of("project", "recordingUnit", "find", "place");
    /** Mots-clés de recherche par champ acceptés ({@code mot-clé:valeur}). */
    static final Set<String> PROJECT_KEYS = Set.of("projet", "project");
    static final int MAX_LIMIT = 20;

    private final SearchRepository searchRepository;
    private final OrganizationListService organizationListService;
    private final EntityProjectResolver projectResolver;

    public GlobalSearchResponse search(ProjectApiCaller caller, InstitutionDTO institution, String q,
                                       Long projectId, List<String> filters, List<String> types, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit doit être compris entre 1 et " + MAX_LIMIT);
        }
        List<String> requestedTypes = requireTypes(types);
        List<String> projectRefs = projectRefs(filters);
        String input = q == null ? "" : q.strip();
        if (input.isEmpty() && projectRefs.isEmpty()) {
            return new GlobalSearchResponse(List.of());
        }

        List<Long> projectIds = resolveProjectIds(institution, projectId, projectRefs);
        if (projectIds != null && projectIds.isEmpty()) {
            return new GlobalSearchResponse(List.of());
        }
        List<Long> visible = organizationListService.visibleProjectIdsOrNull(caller, institution);
        if (visible != null && visible.isEmpty()) {
            return new GlobalSearchResponse(List.of());
        }

        // No requested type means every type: the SQL function reads NULL as "all".
        List<GlobalSearchRow> rows = searchRepository.searchSections(input, institution.getId(), projectIds, visible,
                requestedTypes.isEmpty() ? null : requestedTypes, limit);
        return toResponse(rows, projectNames(rows));
    }

    /** The names of the projects the found fiches belong to, in one query. */
    private Map<Long, String> projectNames(List<GlobalSearchRow> rows) {
        return projectResolver.names(rows.stream()
                .filter(r -> !"project".equals(r.entityType()) && r.projectId() != null)
                .map(GlobalSearchRow::projectId).collect(java.util.stream.Collectors.toSet()));
    }

    private static List<String> requireTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        for (String type : types) {
            if (!TYPES.contains(type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type inconnu : " + type);
            }
        }
        return types;
    }

    /** Valeurs des filtres {@code projet:<ref>} ; tout autre mot-clé est refusé (400). */
    private static List<String> projectRefs(List<String> filters) {
        List<String> refs = new ArrayList<>();
        if (filters == null) {
            return refs;
        }
        for (String filter : filters) {
            int colon = filter == null ? -1 : filter.indexOf(':');
            String key = colon < 0 ? "" : filter.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = colon < 0 ? "" : filter.substring(colon + 1).strip();
            if (!PROJECT_KEYS.contains(key) || value.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtre de recherche non supporté : " + filter);
            }
            refs.add(value);
        }
        return refs;
    }

    /**
     * Un filtre explicite {@code projet:} l'emporte sur le projet courant implicite ; une référence est un id
     * numérique ou l'identifiant/nom exact d'un projet. {@code null} : pas de restriction.
     */
    private List<Long> resolveProjectIds(InstitutionDTO institution, Long projectId, List<String> refs) {
        if (refs.isEmpty()) {
            return projectId == null ? null : List.of(projectId);
        }
        List<Long> ids = new ArrayList<>();
        for (String ref : refs) {
            ids.addAll(searchRepository.findProjectIdsByRef(ref, institution.getId()));
            if (ref.chars().allMatch(Character::isDigit) && ref.length() < 18) {
                ids.add(Long.parseLong(ref));
            }
        }
        return ids.stream().distinct().toList();
    }

    private static GlobalSearchResponse toResponse(List<GlobalSearchRow> rows, Map<Long, String> projectNames) {
        Map<String, List<GlobalSearchRow>> byType = new LinkedHashMap<>();
        TYPES.forEach(type -> byType.put(type, new ArrayList<>()));
        rows.forEach(row -> byType.computeIfAbsent(row.entityType(), k -> new ArrayList<>()).add(row));
        List<GlobalSearchResponse.Section> sections = new ArrayList<>();
        byType.forEach((type, list) -> {
            if (!list.isEmpty()) {
                sections.add(new GlobalSearchResponse.Section(type, list.get(0).total(), list.stream()
                        .map(r -> new GlobalSearchResponse.Item(r.entityId(), r.label(), r.subtitle(), r.projectId(),
                                projectNames.get(r.projectId())))
                        .toList()));
            }
        });
        return new GlobalSearchResponse(sections);
    }
}
