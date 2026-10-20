package fr.siamois.infrastructure.database.repositories.specs;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;

/**
 * The two conditions the tree view needs of a type whose rows have children of their own type through a
 * many-to-many attribute ({@code children}): shared, so each type's spec is a one-liner.
 */
public final class HierarchySpecs {

    private HierarchySpecs() {
    }

    /** Rows that have {@code parentId} as a parent. */
    @NonNull
    public static <E> Specification<E> hasParent(Class<E> type, String childrenAttribute, long parentId) {
        return (root, query, cb) -> {
            Subquery<Long> children = query.subquery(Long.class);
            Root<E> parent = children.from(type);
            Join<E, E> child = parent.join(childrenAttribute);
            children.select(child.get("id")).where(cb.equal(parent.get("id"), parentId));
            return root.get("id").in(children);
        };
    }

    /**
     * The root of a tree inside a context: none of its parents satisfies {@code scope}. Not "has no
     * parent": a row whose parents all lie outside the context (another project's, say) would otherwise
     * have no place in the context's tree at all.
     */
    @NonNull
    public static <E> Specification<E> hasNoParentIn(Class<E> type, String childrenAttribute, Specification<E> scope) {
        return (root, query, cb) -> {
            Subquery<Long> parentsInScope = query.subquery(Long.class);
            Root<E> parent = parentsInScope.from(type);
            Join<E, E> child = parent.join(childrenAttribute);
            Predicate where = cb.equal(child.get("id"), root.get("id"));
            Predicate inScope = scope.toPredicate(parent, query, cb);
            if (inScope != null) {
                where = cb.and(where, inScope);
            }
            parentsInScope.select(parent.get("id")).where(where);
            return cb.not(cb.exists(parentsInScope));
        };
    }
}
