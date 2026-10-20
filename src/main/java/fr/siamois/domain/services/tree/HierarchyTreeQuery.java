package fr.siamois.domain.services.tree;

import fr.siamois.dto.TreePage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The tree view of a list of a hierarchical type (recording units, places, projects…), inside a
 * <em>context</em> — {@code scope} is the set {@code S} of rows the list is limited to (a project's
 * units, a place's children…; "everything" for the organization-wide list).
 *
 * <ul>
 *   <li><b>Roots:</b> the rows of {@code S} none of whose parents is in {@code S} — not "rows with no
 *   parent", or a row whose parents all lie outside the context would have no place in its tree.</li>
 *   <li><b>Children of a row ({@code parentId}):</b> its children that are in {@code S}.</li>
 *   <li><b>A search or filter ({@code matches}):</b> the matches are taken in {@code S}, and only their
 *   ancestors that are themselves in {@code S} are kept (the closure is built level by level, so a row
 *   whose only route to a match goes through a parent outside the context is not in it). The page then
 *   holds the roots (or children) of that closure; the rows that are not matches are context.</li>
 *   <li><b>Flags:</b> each row says whether it has children to show (in {@code S}, and in the closure
 *   when narrowed) and whether it is a match.</li>
 * </ul>
 *
 * The page's total counts the roots (or children) it pages; {@link TreePage#getMatchCount()} is the number
 * of rows of the context that match (all of them, without a search). A type plugs in through
 * {@link Hierarchy}: this class holds the rules, the type only the queries.
 */
public class HierarchyTreeQuery<E, D> {

    /** What the tree needs of one hierarchical type. */
    public interface Hierarchy<E, D> {
        /** Rows that have {@code parentId} as a parent. */
        Specification<E> hasParent(long parentId);

        /** Rows none of whose parents satisfies {@code scope}. */
        Specification<E> hasNoParentIn(Specification<E> scope);

        Specification<E> idIn(Collection<Long> ids);

        /** Both conditions. The default is JPA's; a type (or a test) with its own representation overrides it. */
        default Specification<E> and(Specification<E> a, Specification<E> b) {
            return Specification.where(a).and(b);
        }

        /** No row at all. */
        default Specification<E> none() {
            return (root, query, cb) -> cb.disjunction();
        }

        /** {@code [childId, parentId]} for each of {@code childIds}. */
        List<Object[]> parentEdges(Collection<Long> childIds);

        /** {@code [parentId, childId]} for each of {@code parentIds}. */
        List<Object[]> childEdges(Collection<Long> parentIds);

        List<Long> ids(Specification<E> specs);

        long count(Specification<E> specs);

        /** One page of rows, as the list's DTOs (enriched as the flat list's are). */
        Page<D> page(Specification<E> specs, Pageable pageable);

        Long idOf(D row);

        void flag(D row, boolean hasChildren, boolean match);
    }

    private final Hierarchy<E, D> hierarchy;

    public HierarchyTreeQuery(Hierarchy<E, D> hierarchy) {
        this.hierarchy = hierarchy;
    }

    /**
     * @param scope    the context {@code S}
     * @param matches  the search and filters, or null for none
     * @param ordering the list's sort when it lives in a specification (null/empty otherwise)
     * @param parentId the row whose children are wanted, or null for the roots
     */
    public TreePage<D> page(Specification<E> scope, @Nullable Specification<E> matches,
                            @Nullable Specification<E> ordering, @Nullable Long parentId, Pageable pageable) {
        boolean narrowed = matches != null;
        Set<Long> matchIds = null;
        Set<Long> closure = null;
        if (narrowed) {
            matchIds = new HashSet<>(hierarchy.ids(hierarchy.and(scope, matches)));
            closure = ancestorClosureInScope(scope, matchIds);
        }

        Specification<E> specs = hierarchy.and(scope, parentId == null ? hierarchy.hasNoParentIn(scope) : hierarchy.hasParent(parentId));
        if (ordering != null) {
            specs = hierarchy.and(specs, ordering);
        }
        if (narrowed) {
            specs = hierarchy.and(specs, closure.isEmpty() ? hierarchy.none() : hierarchy.idIn(closure));
        }
        Page<D> page = hierarchy.page(specs, pageable);

        List<Long> ids = page.getContent().stream().map(hierarchy::idOf).filter(Objects::nonNull).toList();
        Set<Long> withChildren = idsHavingChildrenInScope(ids, scope, closure);
        for (D row : page.getContent()) {
            Long id = hierarchy.idOf(row);
            hierarchy.flag(row, withChildren.contains(id), matchIds == null || matchIds.contains(id));
        }
        long matchCount = narrowed ? matchIds.size() : hierarchy.count(scope);
        return TreePage.of(page, matchCount);
    }

    private Set<Long> ancestorClosureInScope(Specification<E> scope, Set<Long> matchIds) {
        Set<Long> closure = new HashSet<>(matchIds);
        Set<Long> frontier = new HashSet<>(matchIds);
        while (!frontier.isEmpty()) {
            Set<Long> parents = new HashSet<>();
            for (Object[] edge : hierarchy.parentEdges(new ArrayList<>(frontier))) {
                parents.add(((Number) edge[1]).longValue());
            }
            parents.removeAll(closure);
            if (parents.isEmpty()) {
                break;
            }
            frontier = new HashSet<>(hierarchy.ids(hierarchy.and(scope, hierarchy.idIn(parents))));
            closure.addAll(frontier);
        }
        return closure;
    }

    /** Which of {@code ids} have a child in {@code scope} (and in {@code closure} when it is not null). */
    private Set<Long> idsHavingChildrenInScope(List<Long> ids, Specification<E> scope, @Nullable Set<Long> closure) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<Object[]> edges = hierarchy.childEdges(ids);
        Set<Long> children = new HashSet<>();
        for (Object[] edge : edges) {
            children.add(((Number) edge[1]).longValue());
        }
        if (closure != null) {
            children.retainAll(closure);
        }
        if (children.isEmpty()) {
            return Set.of();
        }
        Set<Long> inScope = new HashSet<>(hierarchy.ids(hierarchy.and(scope, hierarchy.idIn(children))));
        Set<Long> parents = new HashSet<>();
        for (Object[] edge : edges) {
            if (inScope.contains(((Number) edge[1]).longValue())) {
                parents.add(((Number) edge[0]).longValue());
            }
        }
        return parents;
    }
}
