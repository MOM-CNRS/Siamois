package fr.siamois.domain.services.tree;

import fr.siamois.dto.TreePage;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tree rules — roots, children, closure of ancestors, flags, counts — on an in-memory hierarchy, so
 * what is checked is the behaviour, whatever persistence is behind a type.
 */
class HierarchyTreeQueryTest {

    /** A "specification" that is just a test on ids, which the in-memory hierarchy evaluates. */
    private record Rows(LongPredicate test) implements Specification<Long> {
        @Override
        public Predicate toPredicate(Root<Long> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
            throw new UnsupportedOperationException("evaluated in memory");
        }
    }

    private static Rows in(Long... ids) {
        Set<Long> set = Set.of(ids);
        return new Rows(set::contains);
    }

    private static final Rows EVERYTHING = new Rows(id -> true);

    /** Rows are ids; each maps to its parents (several allowed: polyhierarchy). */
    private static final class InMemory implements HierarchyTreeQuery.Hierarchy<Long, Long> {
        private final Map<Long, List<Long>> parents = new LinkedHashMap<>();
        final Map<Long, Boolean> hasChildren = new LinkedHashMap<>();
        final Map<Long, Boolean> match = new LinkedHashMap<>();

        InMemory row(long id, long... ps) {
            parents.put(id, Arrays.stream(ps).boxed().toList());
            return this;
        }

        private static LongPredicate test(Specification<Long> spec) {
            return ((Rows) spec).test();
        }

        @Override
        public Specification<Long> hasParent(long parentId) {
            return new Rows(id -> parents.getOrDefault(id, List.of()).contains(parentId));
        }

        @Override
        public Specification<Long> hasNoParentIn(Specification<Long> scope) {
            LongPredicate inScope = test(scope);
            return new Rows(id -> parents.getOrDefault(id, List.of()).stream().noneMatch(p -> inScope.test(p)));
        }

        @Override
        public Specification<Long> idIn(Collection<Long> ids) {
            Set<Long> set = new HashSet<>(ids);
            return new Rows(set::contains);
        }

        @Override
        public Specification<Long> and(Specification<Long> a, Specification<Long> b) {
            return new Rows(id -> test(a).test(id) && test(b).test(id));
        }

        @Override
        public Specification<Long> none() {
            return new Rows(id -> false);
        }

        @Override
        public List<Object[]> parentEdges(Collection<Long> childIds) {
            List<Object[]> out = new ArrayList<>();
            for (Long child : childIds) {
                for (Long parent : parents.getOrDefault(child, List.of())) {
                    out.add(new Object[]{child, parent});
                }
            }
            return out;
        }

        @Override
        public List<Object[]> childEdges(Collection<Long> parentIds) {
            List<Object[]> out = new ArrayList<>();
            parents.forEach((child, ps) -> ps.stream().filter(parentIds::contains).forEach(p -> out.add(new Object[]{p, child})));
            return out;
        }

        @Override
        public List<Long> ids(Specification<Long> specs) {
            return parents.keySet().stream().filter(id -> test(specs).test(id)).toList();
        }

        @Override
        public long count(Specification<Long> specs) {
            return ids(specs).size();
        }

        @Override
        public Page<Long> page(Specification<Long> specs, Pageable pageable) {
            List<Long> rows = ids(specs);
            return new PageImpl<>(rows, pageable, rows.size());
        }

        @Override
        public Long idOf(Long row) {
            return row;
        }

        @Override
        public void flag(Long row, boolean hasChildren, boolean match) {
            this.hasChildren.put(row, hasChildren);
            this.match.put(row, match);
        }
    }

    private static TreePage<Long> page(InMemory h, Specification<Long> scope, Specification<Long> matches, Long parent) {
        return new HierarchyTreeQuery<>(h).page(scope, matches, null, parent, PageRequest.of(0, 50));
    }

    // 1 ─ 2 ─ 3     4 (alone)     5 (parent 9, which is outside any context)
    private static InMemory hierarchy() {
        return new InMemory().row(1).row(2, 1).row(3, 2).row(4).row(5, 9).row(9);
    }

    @Test
    void theRootsOfTheWholeListAreTheRowsWithNoParent() {
        InMemory h = hierarchy();

        TreePage<Long> roots = page(h, EVERYTHING, null, null);

        assertThat(roots.getContent()).containsExactly(1L, 4L, 9L);
        assertThat(h.hasChildren).containsEntry(1L, true).containsEntry(4L, false).containsEntry(9L, true);
        // No search: every row is a match, and the match count is the whole context.
        assertThat(h.match.values()).containsOnly(true);
        assertThat(roots.getMatchCount()).isEqualTo(6);
    }

    @Test
    void aRowWhoseParentsAreOutsideTheContextIsARootOfIt() {
        InMemory h = hierarchy();

        // The context holds 1..5 but not 9, the parent of 5.
        TreePage<Long> roots = page(h, in(1L, 2L, 3L, 4L, 5L), null, null);

        assertThat(roots.getContent()).containsExactly(1L, 4L, 5L);
    }

    @Test
    void theChildrenOfARowAreTheOnesInTheContext() {
        InMemory h = new InMemory().row(1).row(2, 1).row(3, 1);

        TreePage<Long> children = page(h, in(1L, 2L), null, 1L);

        assertThat(children.getContent()).containsExactly(2L);
    }

    @Test
    void aRowHasChildrenToShowOnlyIfOneIsInTheContext() {
        InMemory h = new InMemory().row(1).row(2, 1).row(3).row(4, 3);

        page(h, in(1L, 2L, 3L), null, null);

        // 2 is in the context, 4 is not.
        assertThat(h.hasChildren).containsEntry(1L, true).containsEntry(3L, false);
    }

    @Test
    void aSearchKeepsTheRootsLeadingToAMatchAndMarksTheOthersAsContext() {
        InMemory h = hierarchy();

        TreePage<Long> roots = page(h, EVERYTHING, in(3L), null);

        assertThat(roots.getContent()).containsExactly(1L);
        assertThat(h.match).containsEntry(1L, false);
        assertThat(h.hasChildren).containsEntry(1L, true);
        assertThat(roots.getMatchCount()).isEqualTo(1);
    }

    @Test
    void theChildrenOfASearchAreLimitedToTheBranchesLeadingToAMatch() {
        InMemory h = new InMemory().row(1).row(2, 1).row(3, 1).row(4, 2);

        // 4 matches: under 1, only 2 leads to it.
        TreePage<Long> children = page(h, EVERYTHING, in(4L), 1L);

        assertThat(children.getContent()).containsExactly(2L);
        assertThat(h.match).containsEntry(2L, false);
    }

    @Test
    void aMatchingParentWithNoMatchBelowIsALeaf() {
        // 1 matches and has children 2 and 3, none of which matches: a branch with nothing matching in it
        // disappears, so 1 shows no children and opens to nothing.
        InMemory h = new InMemory().row(1).row(2, 1).row(3, 1);

        TreePage<Long> roots = page(h, EVERYTHING, in(1L), null);

        assertThat(roots.getContent()).containsExactly(1L);
        assertThat(h.match).containsEntry(1L, true);
        assertThat(h.hasChildren).containsEntry(1L, false);
        assertThat(page(h, EVERYTHING, in(1L), 1L).getContent()).isEmpty();
    }

    @Test
    void aMatchingParentOpensOnlyOnTheBranchesWithAMatchBelow() {
        // 1 matches; 2 has a matching child 4, 3 has nothing matching: only 2 is shown under 1.
        InMemory h = new InMemory().row(1).row(2, 1).row(3, 1).row(4, 2);

        TreePage<Long> children = page(h, EVERYTHING, in(1L, 4L), 1L);

        assertThat(children.getContent()).containsExactly(2L);
    }

    @Test
    void theClosureStopsAtAParentOutsideTheContext() {
        // 3's parent 2 is outside the context; 2's parent 1 is inside it. Inside the context, 3 is a root and
        // 1 leads nowhere: 1 must not show up as a parent of the match.
        InMemory h = new InMemory().row(1).row(2, 1).row(3, 2);

        TreePage<Long> roots = page(h, in(1L, 3L), in(3L), null);

        assertThat(roots.getContent()).containsExactly(3L);
        assertThat(h.match).containsEntry(3L, true);
    }

    @Test
    void aSearchThatMatchesNothingIsEmpty() {
        InMemory h = hierarchy();

        TreePage<Long> roots = page(h, EVERYTHING, in(), null);

        assertThat(roots.getContent()).isEmpty();
        assertThat(roots.getMatchCount()).isZero();
    }

    @Test
    void aRowUnderTwoParentsIsAChildOfEach() {
        InMemory h = new InMemory().row(1).row(2).row(3, 1, 2);

        assertThat(page(h, EVERYTHING, null, 1L).getContent()).containsExactly(3L);
        assertThat(page(h, EVERYTHING, null, 2L).getContent()).containsExactly(3L);
        // ...and the rows above it each show it as a reason to open.
        page(h, EVERYTHING, null, null);
        assertThat(h.hasChildren).containsEntry(1L, true).containsEntry(2L, true);
        // It is one row in the count, not two.
        assertThat(page(h, EVERYTHING, null, null).getMatchCount()).isEqualTo(3);
    }

    @Test
    void aRowUnderTwoParentsIsCountedOnceWhenItMatches() {
        InMemory h = new InMemory().row(1).row(2).row(3, 1, 2);

        TreePage<Long> roots = page(h, EVERYTHING, in(3L), null);

        assertThat(roots.getContent()).containsExactly(1L, 2L);
        assertThat(roots.getMatchCount()).isEqualTo(1);
    }
}
