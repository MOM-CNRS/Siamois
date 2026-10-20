package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two conditions of the tree view shared by every hierarchical type ({@link HierarchySpecs}), and the
 * types' own one-liners over them. Mocked criteria (no integration database in this codebase): what is
 * checked is the shape of the query.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class HierarchySpecsTest {

    private final Root<Object> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
    private final Subquery<Long> sub = mock(Subquery.class);
    private final Root<Object> parent = mock(Root.class);
    private final Join<Object, Object> child = mock(Join.class);
    private final Path<Object> idPath = mock(Path.class);
    private final Predicate sameId = mock(Predicate.class);
    private final Predicate inSubquery = mock(Predicate.class);
    private final Predicate exists = mock(Predicate.class);
    private final Predicate notExists = mock(Predicate.class);

    private void wire(Class<?> type, String attribute) {
        when(query.subquery(Long.class)).thenReturn((Subquery) sub);
        when(sub.from((Class) type)).thenReturn((Root) parent);
        when(parent.join(attribute)).thenReturn((Join) child);
        when(child.get("id")).thenReturn(idPath);
        when(root.get("id")).thenReturn(idPath);
        when(parent.get("id")).thenReturn(idPath);
        when(cb.equal(any(Expression.class), any(Expression.class))).thenReturn(sameId);
        when(cb.equal(any(Expression.class), any(Object.class))).thenReturn(sameId);
        when(sub.select(any())).thenReturn(sub);
    }

    @Test
    void hasParent_isTheRowsWhoseIdIsAmongTheChildrenOfThatParent() {
        wire(RecordingUnit.class, "children");
        when(idPath.in(sub)).thenReturn(inSubquery);

        Predicate result = (Predicate) HierarchySpecs.hasParent(RecordingUnit.class, "children", 7L)
                .toPredicate((Root) root, query, cb);

        assertSame(inSubquery, result);
        verify(sub).where(sameId);
    }

    @Test
    void hasNoParentIn_isTheRowsNoParentOfWhichSatisfiesTheContext() {
        wire(ActionUnit.class, "children");
        Predicate inScope = mock(Predicate.class);
        Predicate both = mock(Predicate.class);
        Specification<ActionUnit> scope = (r, q, c) -> inScope;
        when(cb.and(sameId, inScope)).thenReturn(both);
        when(cb.exists(sub)).thenReturn(exists);
        when(cb.not(exists)).thenReturn(notExists);

        Predicate result = (Predicate) HierarchySpecs.hasNoParentIn(ActionUnit.class, "children", scope)
                .toPredicate((Root) root, query, cb);

        assertSame(notExists, result);
        verify(sub).where(both);
    }

    @Test
    void theProjectsAndRecordingUnitsConditionsJoinTheirChildrenAttribute() {
        wire(ActionUnit.class, "children");
        when(idPath.in(sub)).thenReturn(inSubquery);
        when(cb.exists(sub)).thenReturn(exists);
        when(cb.not(exists)).thenReturn(notExists);

        assertSame(inSubquery, ActionUnitSpec.hasParent(3L).toPredicate((Root) root, query, cb));
        assertSame(notExists, ActionUnitSpec.hasNoParentIn((r, q, c) -> null).toPredicate((Root) root, query, cb));
        verify(parent, org.mockito.Mockito.times(2)).join("children");
    }
}
