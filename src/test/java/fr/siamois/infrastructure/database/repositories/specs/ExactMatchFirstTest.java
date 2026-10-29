package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.phase.Phase;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExactMatchFirstTest {

    @Mock
    private JpaSpecificationExecutor<Phase> repository;
    @Mock
    private Root<Phase> root;
    @Mock
    private CriteriaQuery<Object> query;
    @Mock
    private CriteriaBuilder cb;

    private final Specification<Phase> base = (r, q, b) -> null;

    /**
     * Without a searched text the repository gets the specification and the pageable untouched.
     */
    @Test
    void findAll_withoutSearch_delegatesUnchanged() {
        Pageable pageable = PageRequest.of(1, 20, Sort.by("creationTime").descending());

        ExactMatchFirst.findAll(repository, base, pageable, "identifier", "  ");

        verify(repository).findAll(base, pageable);
    }

    /**
     * With a searched text the sort leaves the pageable (it moves into the specification), the page stays.
     */
    @Test
    @SuppressWarnings("unchecked")
    void findAll_withSearch_movesSortOutOfPageable() {
        Pageable pageable = PageRequest.of(1, 20, Sort.by("creationTime").descending());
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        ExactMatchFirst.findAll(repository, base, pageable, "identifier", "EXEMPLE");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    /**
     * The exact match on the lowered attribute comes first, the orders already set on the query after it.
     */
    @Test
    @SuppressWarnings("unchecked")
    void rank_putsExactMatchBeforeExistingOrders() {
        Path<Object> identifier = mock(Path.class);
        Expression<String> lowered = mock(Expression.class);
        Predicate equal = mock(Predicate.class);
        CriteriaBuilder.Case<Integer> whenCase = mock(CriteriaBuilder.Case.class);
        Expression<Integer> rankExpr = mock(Expression.class);
        Order rankOrder = mock(Order.class);
        Order existing = mock(Order.class);
        when(query.getResultType()).thenReturn(Object.class);
        when(query.isDistinct()).thenReturn(false);
        when(query.getOrderList()).thenReturn(List.of(existing));
        when(root.get("identifier")).thenReturn(identifier);
        when(cb.lower(any())).thenReturn(lowered);
        Predicate exact = mock(Predicate.class);
        when(cb.equal(lowered, "exemple")).thenReturn(equal);
        when(cb.or(equal)).thenReturn(exact);
        when(cb.<Integer>selectCase()).thenReturn(whenCase);
        when(whenCase.when(exact, 0)).thenReturn(whenCase);
        when(whenCase.otherwise(1)).thenReturn(rankExpr);
        when(cb.asc(rankExpr)).thenReturn(rankOrder);

        ExactMatchFirst.rank(base, Sort.unsorted(), List.of("identifier"), "EXEMPLE").toPredicate(root, query, cb);

        verify(query).orderBy(List.of(rankOrder, existing));
    }

    /**
     * With several attributes, a row is an exact match when any of them equals the searched text.
     */
    @Test
    @SuppressWarnings("unchecked")
    void rank_severalAttributes_exactWhenAnyEquals() {
        Path<String> name = mock(Path.class);
        Path<String> identifier = mock(Path.class);
        Expression<String> lowerName = mock(Expression.class);
        Expression<String> lowerIdentifier = mock(Expression.class);
        Predicate nameEqual = mock(Predicate.class);
        Predicate identifierEqual = mock(Predicate.class);
        CriteriaBuilder.Case<Integer> whenCase = mock(CriteriaBuilder.Case.class);
        when(query.getResultType()).thenReturn(Object.class);
        when(query.getOrderList()).thenReturn(List.of());
        when(root.<String>get("name")).thenReturn(name);
        when(root.<String>get("identifier")).thenReturn(identifier);
        when(cb.lower(name)).thenReturn(lowerName);
        when(cb.lower(identifier)).thenReturn(lowerIdentifier);
        when(cb.equal(lowerName, "fouille")).thenReturn(nameEqual);
        when(cb.equal(lowerIdentifier, "fouille")).thenReturn(identifierEqual);
        when(cb.<Integer>selectCase()).thenReturn(whenCase);
        when(whenCase.when(any(), eq(0))).thenReturn(whenCase);

        ExactMatchFirst.rank(base, Sort.unsorted(), List.of("name", "identifier"), "Fouille").toPredicate(root, query, cb);

        verify(cb).or(new Predicate[]{nameEqual, identifierEqual});
    }

    /**
     * The count query of a page is never ordered.
     */
    @Test
    void rank_countQuery_setsNoOrder() {
        when(query.getResultType()).thenReturn((Class) Long.class);

        ExactMatchFirst.rank(base, Sort.unsorted(), List.of("identifier"), "EXEMPLE").toPredicate(root, query, cb);

        verify(query, never()).orderBy(any(List.class));
        verifyNoInteractions(cb);
    }

    /**
     * A distinct query keeps its orders but gets no ranking expression, which PostgreSQL would refuse.
     */
    @Test
    void rank_distinctQuery_keepsOrdersWithoutRanking() {
        Order existing = mock(Order.class);
        when(query.getResultType()).thenReturn(Object.class);
        when(query.isDistinct()).thenReturn(true);
        when(query.getOrderList()).thenReturn(List.of(existing));

        ExactMatchFirst.rank(base, Sort.unsorted(), List.of("identifier"), "EXEMPLE").toPredicate(root, query, cb);

        verify(query).orderBy(List.of(existing));
        verifyNoInteractions(cb);
    }
}
