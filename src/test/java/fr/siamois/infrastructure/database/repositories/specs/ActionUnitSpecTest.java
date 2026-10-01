package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.permissions.PermissionScopeType;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionUnitSpecTest {

    @Mock
    private Root<ActionUnit> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path<List<?>> collectionPath;
    @Mock
    private Expression<Integer> sizeExpr;
    @Mock
    private Order order;

    @Test
    void orderByRecordingUnitCount_ascending_setsAscOrderOnQuery() {
        when(root.<List<?>>get("recordingUnitList")).thenReturn(collectionPath);
        when(cb.size(collectionPath)).thenReturn(sizeExpr);
        when(cb.asc(sizeExpr)).thenReturn(order);
        when(cb.conjunction()).thenReturn(null);

        Specification<ActionUnit> spec = ActionUnitSpec.orderByRecordingUnitCount(Sort.Direction.ASC);
        spec.toPredicate(root, query, cb);

        verify(query).orderBy(order);
        verify(cb).asc(sizeExpr);
    }

    @Test
    void orderByRecordingUnitCount_descending_setsDescOrderOnQuery() {
        when(root.<List<?>>get("recordingUnitList")).thenReturn(collectionPath);
        when(cb.size(collectionPath)).thenReturn(sizeExpr);
        when(cb.desc(sizeExpr)).thenReturn(order);
        when(cb.conjunction()).thenReturn(null);

        Specification<ActionUnit> spec = ActionUnitSpec.orderByRecordingUnitCount(Sort.Direction.DESC);
        spec.toPredicate(root, query, cb);

        verify(query).orderBy(order);
        verify(cb).desc(sizeExpr);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void hasProjectMembership_buildsExistsSubquery_scopedToProjectAndActionUnit() {
        Subquery<Long> subquery = mock(Subquery.class);
        Root<PersonProfileAssignment> assignment = mock(Root.class);
        Path personPath = mock(Path.class);
        Join profilePath = mock(Join.class);
        Path scopePath = mock(Path.class);
        Path actionUnitPath = mock(Path.class);
        Predicate personEq = mock(Predicate.class);
        Predicate scopeEq = mock(Predicate.class);
        Predicate actionUnitEq = mock(Predicate.class);
        Predicate existsPredicate = mock(Predicate.class);

        when(query.subquery(Long.class)).thenReturn(subquery);
        when(subquery.from(PersonProfileAssignment.class)).thenReturn(assignment);
        when(assignment.join("profile")).thenReturn(profilePath);
        when(assignment.get("person")).thenReturn(personPath);
        when(personPath.get("id")).thenReturn(personPath);
        when(profilePath.get(ActionUnitSpec.SCOPE)).thenReturn(scopePath);
        when(profilePath.get("actionUnit")).thenReturn(actionUnitPath);
        when(cb.literal(1L)).thenReturn(null);
        when(cb.equal(personPath, 42L)).thenReturn(personEq);
        when(cb.equal(scopePath, PermissionScopeType.PROJECT)).thenReturn(scopeEq);
        when(cb.equal(actionUnitPath, root)).thenReturn(actionUnitEq);
        when(subquery.select(any())).thenReturn(subquery);
        when(subquery.where(personEq, scopeEq, actionUnitEq)).thenReturn(subquery);
        when(cb.exists(subquery)).thenReturn(existsPredicate);

        Specification<ActionUnit> spec = ActionUnitSpec.hasProjectMembership(42L);
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(existsPredicate, result);
        verify(cb).equal(scopePath, PermissionScopeType.PROJECT);
        verify(cb).equal(actionUnitPath, root);
        verify(cb).exists(subquery);
    }

    // ------------------------------------------------------------------
    // cursor — the prev/next sibling navigation predicate
    // ------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void cursor_missingValueOrId_returnsConjunction() {
        when(cb.conjunction()).thenReturn(null);

        Specification<ActionUnit> spec = ActionUnitSpec.cursor("name", Sort.Direction.ASC, null, 1L, true);
        spec.toPredicate(root, query, cb);

        verify(cb).conjunction();
        verifyNoMoreInteractions(root);
    }

    /**
     * Forward on an ASC sort: the field must be strictly greater, OR equal with a greater id —
     * exactly what walking "next" in {@code name:asc} order means.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void cursor_forwardAscending_comparesFieldGreaterThanOrEqualWithGreaterId() {
        Path fieldPath = mock(Path.class);
        Path idPath = mock(Path.class);
        Predicate fieldGt = mock(Predicate.class);
        Predicate fieldEq = mock(Predicate.class);
        Predicate idGt = mock(Predicate.class);
        Predicate andPredicate = mock(Predicate.class);
        Predicate orPredicate = mock(Predicate.class);

        when(root.get("name")).thenReturn(fieldPath);
        when(root.get("id")).thenReturn(idPath);
        when(cb.greaterThan(fieldPath, "Foss")).thenReturn(fieldGt);
        when(cb.equal(fieldPath, "Foss")).thenReturn(fieldEq);
        when(cb.greaterThan(idPath, 10L)).thenReturn(idGt);
        when(cb.and(fieldEq, idGt)).thenReturn(andPredicate);
        when(cb.or(fieldGt, andPredicate)).thenReturn(orPredicate);

        Specification<ActionUnit> spec = ActionUnitSpec.cursor("name", Sort.Direction.ASC, "Foss", 10L, true);
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(orPredicate, result);
        verify(cb).greaterThan(fieldPath, "Foss");
        verify(cb).greaterThan(idPath, 10L);
    }

    /**
     * Walking "previous" on the same {@code name:asc} order reverses BOTH comparisons: the field
     * must be strictly less, and the id tie-break also flips to less-than — otherwise "previous"
     * would land on the far end of the set instead of the adjacent row.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void cursor_backwardOnAscendingSort_comparesFieldAndIdLessThan() {
        Path fieldPath = mock(Path.class);
        Path idPath = mock(Path.class);
        Predicate fieldLt = mock(Predicate.class);
        Predicate fieldEq = mock(Predicate.class);
        Predicate idLt = mock(Predicate.class);
        Predicate andPredicate = mock(Predicate.class);
        Predicate orPredicate = mock(Predicate.class);

        when(root.get("name")).thenReturn(fieldPath);
        when(root.get("id")).thenReturn(idPath);
        when(cb.lessThan(fieldPath, "Foss")).thenReturn(fieldLt);
        when(cb.equal(fieldPath, "Foss")).thenReturn(fieldEq);
        when(cb.lessThan(idPath, 10L)).thenReturn(idLt);
        when(cb.and(fieldEq, idLt)).thenReturn(andPredicate);
        when(cb.or(fieldLt, andPredicate)).thenReturn(orPredicate);

        Specification<ActionUnit> spec = ActionUnitSpec.cursor("name", Sort.Direction.ASC, "Foss", 10L, false);
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(orPredicate, result);
        verify(cb).lessThan(fieldPath, "Foss");
        verify(cb).lessThan(idPath, 10L);
    }

    /**
     * A DESC sort field flips the field comparison back to greaterThan for "forward" — the id
     * tie-break stays greaterThan too, since it only flips with the walk direction, not with the
     * sort's own direction (id is always the ASC second criterion of the effective order).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void cursor_forwardOnDescendingSort_comparesFieldLessThanButIdGreaterThan() {
        Path fieldPath = mock(Path.class);
        Path idPath = mock(Path.class);
        Predicate fieldLt = mock(Predicate.class);
        Predicate fieldEq = mock(Predicate.class);
        Predicate idGt = mock(Predicate.class);
        Predicate andPredicate = mock(Predicate.class);
        Predicate orPredicate = mock(Predicate.class);

        when(root.get("creationTime")).thenReturn(fieldPath);
        when(root.get("id")).thenReturn(idPath);
        when(cb.lessThan(fieldPath, "T")).thenReturn(fieldLt);
        when(cb.equal(fieldPath, "T")).thenReturn(fieldEq);
        when(cb.greaterThan(idPath, 10L)).thenReturn(idGt);
        when(cb.and(fieldEq, idGt)).thenReturn(andPredicate);
        when(cb.or(fieldLt, andPredicate)).thenReturn(orPredicate);

        Specification<ActionUnit> spec = ActionUnitSpec.cursor("creationTime", Sort.Direction.DESC, "T", 10L, true);
        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(orPredicate, result);
        verify(cb).lessThan(fieldPath, "T");
        verify(cb).greaterThan(idPath, 10L);
    }
}
