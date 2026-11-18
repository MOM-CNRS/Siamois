package fr.siamois.infrastructure.database.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the pages of the app are about, whatever its type: an entity, or a saved view of a list.
 */
@Repository
public class PageTargetRepository {

    /** The kind of target of a list opened on a saved view. */
    public static final String SAVED_VIEW = "saved-view";

    /** Each kind of target, by its page's path segment, with the JPA entity it is read from. */
    private static final Map<String, String> ENTITY_OF_KIND = Map.of(
            "action-unit", "ActionUnit",
            "recording-unit", "RecordingUnit",
            "spatial-unit", "SpatialUnit",
            "specimen", "Specimen",
            "container", "Container",
            "phase", "Phase",
            "study", "Study",
            "document", "Document",
            SAVED_VIEW, "UiTableView");

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Whether this path segment names a kind of target.
     */
    public boolean isKnownKind(String kind) {
        return ENTITY_OF_KIND.containsKey(kind);
    }

    /**
     * Those of these ids that still exist for this kind of target.
     */
    public Set<Long> existingIds(String kind, Collection<Long> ids) {
        return new HashSet<>(entityManager.createQuery(
                        "select e.id from " + ENTITY_OF_KIND.get(kind) + " e where e.id in :ids", // NOSONAR S2077: constants of ENTITY_OF_KIND
                        Long.class)
                .setParameter("ids", ids)
                .getResultList());
    }
}
