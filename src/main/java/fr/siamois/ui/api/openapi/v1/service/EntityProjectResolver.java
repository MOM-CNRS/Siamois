package fr.siamois.ui.api.openapi.v1.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The project a stored page belongs to, read when the page is listed (bookmarks, history) rather than
 * kept with it: a renamed project is then shown under its new name, and entries saved before this existed
 * need no migration.
 *
 * <p>A page is an app path ({@code /recording-unit/12}). Only the pages of an entity owned by a project
 * have one: a project's own page (it <em>is</em> the project), a place (owned by the institution), a list
 * and any path that is not an entity's give none. Projects outside {@code accessibleInstitutionIds}
 * are left out, as the entities' own endpoints do.</p>
 */
@Service
public class EntityProjectResolver {

    /** The project an entity belongs to, as the chip shows it. */
    public record ProjectRef(Long id, String name) {
    }

    /** Path segment of each project-owned entity type, with the JPA entity it is read from. */
    private static final Map<String, String> ENTITY_OF_SEGMENT = Map.of(
            "recording-unit", "RecordingUnit",
            "specimen", "Specimen",
            "container", "Container",
            "phase", "Phase",
            "study", "Study",
            "document", "Document");

    /**
     * The text of a sidebar button as HTML (set it with {@code escape="false"}): the entry's name, then the project
     * name when there is one, so it lives inside the button and the button keeps the full line. Both texts are escaped here.
     */
    public static String labelWithProject(String title, String projectName) {
        String name = "<span class=\"sia-entry-title\">" + HtmlUtils.htmlEscape(title == null ? "" : title) + "</span>";
        if (projectName == null || projectName.isBlank()) {
            return name;
        }
        return name + "<span class=\"sia-project-name\">" + HtmlUtils.htmlEscape(projectName) + "</span>";
    }

    @PersistenceContext
    private EntityManager entityManager;

    /** The project of each path that has one, keyed by the path as given. */
    @Transactional(readOnly = true)
    public Map<String, ProjectRef> resolve(Collection<String> uris, Set<Long> accessibleInstitutionIds) {
        Map<String, ProjectRef> result = new HashMap<>();
        if (uris == null || uris.isEmpty() || accessibleInstitutionIds == null || accessibleInstitutionIds.isEmpty()) {
            return result;
        }
        Map<String, Map<Long, List<String>>> urisByEntity = new HashMap<>();
        for (String uri : new HashSet<>(uris)) {
            String[] parsed = parse(uri);
            if (parsed != null) {
                urisByEntity.computeIfAbsent(ENTITY_OF_SEGMENT.get(parsed[0]), k -> new HashMap<>())
                        .computeIfAbsent(Long.parseLong(parsed[1]), k -> new ArrayList<>()).add(uri);
            }
        }
        urisByEntity.forEach((entity, byId) -> {
            List<Object[]> rows = entityManager.createQuery(
                            "select e.id, a.id, coalesce(a.name, a.fullIdentifier, a.identifier) from " + entity // NOSONAR S2077: constants of ENTITY_OF_SEGMENT
                                    + " e join e.actionUnit a where e.id in :ids and a.createdByInstitution.id in :institutions",
                            Object[].class)
                    .setParameter("ids", byId.keySet())
                    .setParameter("institutions", accessibleInstitutionIds)
                    .getResultList();
            for (Object[] row : rows) {
                ProjectRef ref = new ProjectRef((Long) row[1], (String) row[2]);
                byId.get((Long) row[0]).forEach(uri -> result.put(uri, ref));
            }
        });
        return result;
    }

    /** The name of each of these projects (its name, else its identifier), by id. */
    @Transactional(readOnly = true)
    public Map<Long, String> names(Collection<Long> projectIds) {
        Map<Long, String> names = new HashMap<>();
        if (projectIds == null || projectIds.isEmpty()) {
            return names;
        }
        entityManager.createQuery("select a.id, coalesce(a.name, a.fullIdentifier, a.identifier) from ActionUnit a where a.id in :ids", Object[].class)
                .setParameter("ids", projectIds)
                .getResultList()
                .forEach(row -> names.put((Long) row[0], (String) row[1]));
        return names;
    }

    /** {@code [segment, id]} of {@code /recording-unit/12?x=y}; null when it is not a project-owned entity's page. */
    private static String[] parse(String uri) {
        if (uri == null) {
            return null;
        }
        String[] parts = uri.split("\\?", 2)[0].replaceFirst("^/", "").split("/");
        if (parts.length != 2 || !ENTITY_OF_SEGMENT.containsKey(parts[0]) || !parts[1].matches("\\d{1,18}")) {
            return null;
        }
        return parts;
    }
}
