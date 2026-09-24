package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingResource;
import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Previous/next for every entity fiche except Project (which has its own cursor-based lookup,
 * {@link ProjectApiService#findSiblings}): the neighbours of an entity inside its scope — its
 * project for the project-owned kinds, its organization for places — in creation order.
 *
 * <p>Callers must have checked access to the current entity first (each type's own
 * requireAccessible…): a project or organization the caller can open implies its entities are
 * listable, which is exactly what the scoped list endpoints already assume.</p>
 */
@Service
public class EntitySiblingsService {

    /**
     * Fixed per-kind query parts (never user input): the JPA entity, the label expression, the
     * scope path and the navigation URI prefix (JSF's own entityRessourceUri()).
     */
    public enum Kind {
        RECORDING_UNIT("RecordingUnit", "e.fullIdentifier", "e.actionUnit.id", "/recording-unit/"),
        FIND("Specimen", "e.fullIdentifier", "e.actionUnit.id", "/specimen/"),
        CONTAINER("Container", "e.identifier", "e.actionUnit.id", "/container/"),
        PHASE("Phase", "coalesce(e.identifier, e.title)", "e.actionUnit.id", "/phase/"),
        PLACE("SpatialUnit", "e.name", "e.createdByInstitution.id", "/spatial-unit/");

        private final String entity;
        private final String label;
        private final String scopePath;
        private final String uriPrefix;

        Kind(String entity, String label, String scopePath, String uriPrefix) {
            this.entity = entity;
            this.label = label;
            this.scopePath = scopePath;
            this.uriPrefix = uriPrefix;
        }
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public SiblingsResource findSiblings(Kind kind, Long scopeId, Long currentId) {
        if (scopeId == null || currentId == null) {
            return new SiblingsResource(null, null);
        }
        String jpql = "select e.id, " + kind.label + " from " + kind.entity + " e where " + kind.scopePath
                + " = :scope order by e.creationTime asc, e.id asc";
        List<Object[]> rows = entityManager.createQuery(jpql, Object[].class)
                .setParameter("scope", scopeId)
                .getResultList();
        return pick(kind, rows, currentId);
    }

    /** The neighbours of {@code currentId} in {@code rows} ({@code [id, label]}, already ordered), looping. */
    static SiblingsResource pick(Kind kind, List<Object[]> rows, Long currentId) {
        int index = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (Objects.equals(rows.get(i)[0], currentId)) {
                index = i;
                break;
            }
        }
        if (index < 0 || rows.size() < 2) {
            return new SiblingsResource(null, null);
        }
        Object[] previous = rows.get((index - 1 + rows.size()) % rows.size());
        Object[] next = rows.get((index + 1) % rows.size());
        return new SiblingsResource(toResource(kind, previous), toResource(kind, next));
    }

    private static SiblingResource toResource(Kind kind, Object[] row) {
        String id = String.valueOf(row[0]);
        String label = row[1] != null ? String.valueOf(row[1]) : id;
        return new SiblingResource(id, label, kind.uriPrefix + id);
    }
}
