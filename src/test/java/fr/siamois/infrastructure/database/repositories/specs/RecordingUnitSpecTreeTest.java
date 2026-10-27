package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link RecordingUnitSpec#hasNoParentIn}: the root of a tree inside a context is a unit with no parent
 * <em>in that context</em>. Mocked criteria (the codebase has no integration-test database): what is checked
 * is the shape of the query — a NOT EXISTS over the parents that satisfy the context.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class RecordingUnitSpecTreeTest {

    private final Root<RecordingUnit> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
    private final Subquery<Long> sub = mock(Subquery.class);
    private final Root<RecordingUnit> parent = mock(Root.class);
    private final Join<RecordingUnit, RecordingUnit> child = mock(Join.class);
    private final Path<Object> idPath = mock(Path.class);
    private final Predicate sameChild = mock(Predicate.class);
    private final Predicate both = mock(Predicate.class);
    private final Predicate exists = mock(Predicate.class);
    private final Predicate notExists = mock(Predicate.class);

    private void wire() {
        when(query.subquery(Long.class)).thenReturn((Subquery) sub);
        when(sub.from(RecordingUnit.class)).thenReturn(parent);
        when(parent.join(anyString())).thenReturn((Join) child);
        when(child.get("id")).thenReturn(idPath);
        when(root.get("id")).thenReturn(idPath);
        when(parent.get("id")).thenReturn(idPath);
        when(cb.equal(any(jakarta.persistence.criteria.Expression.class), any(jakarta.persistence.criteria.Expression.class))).thenReturn(sameChild);
        when(sub.select(any())).thenReturn(sub);
        when(cb.exists(sub)).thenReturn(exists);
        when(cb.not(exists)).thenReturn(notExists);
    }

    @Test
    void aUnitIsARootWhenNoParentOfItSatisfiesTheContext() {
        wire();
        Predicate inScope = mock(Predicate.class);
        Specification<RecordingUnit> scope = (r, q, c) -> inScope;
        when(cb.and(sameChild, inScope)).thenReturn(both);

        Predicate result = RecordingUnitSpec.hasNoParentIn(scope).toPredicate(root, query, cb);

        assertSame(notExists, result);
        verify(sub).where(both);
    }

    @Test
    void aContextWithoutAPredicateStillLooksAtTheParents() {
        wire();
        Specification<RecordingUnit> everything = (r, q, c) -> null;

        Predicate result = RecordingUnitSpec.hasNoParentIn(everything).toPredicate(root, query, cb);

        assertSame(notExists, result);
        verify(sub).where(sameChild);
    }
}
