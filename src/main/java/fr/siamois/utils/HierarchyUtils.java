package fr.siamois.utils;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Operations on the parent / child links of entities that form a hierarchy of their own kind (recording
 * units, specimens, places), mapped as a many-to-many whose owning side is the parent's children.
 */
public final class HierarchyUtils {

    private HierarchyUtils() {
        throw new UnsupportedOperationException("HierarchyUtils should not be instantiated");
    }

    /**
     * Removes the links between {@code node} and its direct parents and children whose id is in
     * {@code ids}, on both sides so the flush deletes the join rows (and audits it).
     *
     * @param node       the entity to detach
     * @param parentsOf  an entity's parents (inverse side)
     * @param childrenOf an entity's children (owning side)
     * @param idOf       an entity's id
     * @param ids        ids of the entities to detach it from
     * @param <E>        the entity type
     */
    public static <E> void detachFrom(E node, Function<E, Set<E>> parentsOf, Function<E, Set<E>> childrenOf,
                                      Function<E, Long> idOf, Set<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        for (E parent : among(parentsOf.apply(node), idOf, ids)) {
            childrenOf.apply(parent).remove(node);
            parentsOf.apply(node).remove(parent);
        }
        for (E child : among(childrenOf.apply(node), idOf, ids)) {
            parentsOf.apply(child).remove(node);
            childrenOf.apply(node).remove(child);
        }
    }

    /**
     * The entities of {@code related} whose id is in {@code ids}, copied so the caller may change {@code related}.
     */
    private static <E> List<E> among(Set<E> related, Function<E, Long> idOf, Set<Long> ids) {
        if (related == null) {
            return List.of();
        }
        return related.stream().filter(entity -> ids.contains(idOf.apply(entity))).toList();
    }
}
