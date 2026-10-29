package fr.siamois.domain.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;

/**
 * Refuses the hierarchy links that would close a loop (an entity ending up among its own ancestors).
 * Every write of a parent/child link on an existing hierarchy goes through here, whatever the form,
 * table or import that triggers it.
 */
@Service
public class HierarchyCycleGuard {

    /** Entity kinds that have a parent/child hierarchy. */
    public enum Kind {
        RECORDING_UNIT,
        SPECIMEN,
        SPATIAL_UNIT,
        CONTAINER
    }

    private static final String CYCLE_MESSAGE = "Impossible de créer la relation : cycle hiérarchique détecté";

    /** The edges of each hierarchy as a {@code (parent_id, child_id)} native subquery. */
    private static final Map<Kind, String> EDGES = Map.of(
            Kind.RECORDING_UNIT, "SELECT fk_parent_id AS parent_id, fk_child_id AS child_id FROM recording_unit_hierarchy",
            Kind.SPECIMEN, "SELECT fk_parent_id AS parent_id, fk_child_id AS child_id FROM specimen_hierarchy",
            Kind.SPATIAL_UNIT, "SELECT fk_parent_id AS parent_id, fk_child_id AS child_id FROM spatial_hierarchy",
            Kind.CONTAINER, "SELECT fk_parent_id AS parent_id, container_id AS child_id FROM container "
                    + "WHERE fk_parent_id IS NOT NULL");

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * @throws IllegalStateException (mapped to HTTP 409) when linking {@code childId} under
     *                               {@code parentId} would make an entity its own ancestor
     */
    @Transactional(readOnly = true)
    public void requireNoCycle(Kind kind, long parentId, long childId) {
        if (parentId == childId || isAncestorOrSelf(kind, childId, parentId)) {
            throw new IllegalStateException(CYCLE_MESSAGE);
        }
    }

    /**
     * Checks the links an entity is about to get. {@code selfId} is the entity being saved, null when it
     * is new (it then has no stored links, but its new parents and children still must not be related
     * the wrong way round). Only the links being added need to be passed.
     */
    @Transactional(readOnly = true)
    public void requireNoCycleThrough(Kind kind, Long selfId, Collection<Long> newParentIds,
                                      Collection<Long> newChildIds) {
        for (Long parentId : newParentIds) {
            if (selfId != null) {
                requireNoCycle(kind, parentId, selfId);
            }
            // parent -> self -> child closes a loop when the child is already above the parent
            for (Long childId : newChildIds) {
                requireNoCycle(kind, parentId, childId);
            }
        }
        if (selfId != null) {
            for (Long childId : newChildIds) {
                requireNoCycle(kind, selfId, childId);
            }
        }
    }

    /** Whether {@code candidateId} is {@code entityId} or one of its ancestors, per the stored links. */
    private boolean isAncestorOrSelf(Kind kind, long candidateId, long entityId) {
        Number found = (Number) entityManager.createNativeQuery(
                        "WITH RECURSIVE ascend(id) AS ("
                                + " SELECT CAST(:entityId AS BIGINT)"
                                + " UNION"
                                + " SELECT e.parent_id FROM (" + EDGES.get(kind) + ") e JOIN ascend a ON e.child_id = a.id"
                                + ") SELECT COUNT(*) FROM ascend WHERE id = :candidateId")
                .setParameter("entityId", entityId)
                .setParameter("candidateId", candidateId)
                .getSingleResult();
        return found != null && found.longValue() > 0;
    }
}
