package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingUnitSpecRelationshipSortTest {

    @Mock
    private Root<RecordingUnit> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Subquery<Long> subquery;
    @Mock
    private Root<StratigraphicRelationship> relRoot;
    @Mock
    private Path<Object> unit1Path;
    @Mock
    private Path<Object> unit2Path;
    @Mock
    private jakarta.persistence.criteria.Expression<Long> countExpr;
    @Mock
    private Predicate unit1Eq;
    @Mock
    private Predicate unit2Eq;
    @Mock
    private Predicate orPredicate;
    @Mock
    private Order order;

    @Test
    void orderByRelationshipCount_buildsCorrelatedSubqueryAndOrdersByIt() {
        when(query.subquery(Long.class)).thenReturn(subquery);
        when(subquery.from(StratigraphicRelationship.class)).thenReturn(relRoot);
        when(subquery.select(countExpr)).thenReturn(subquery);
        when(cb.count(relRoot)).thenReturn(countExpr);
        when(relRoot.<Object>get("unit1")).thenReturn(unit1Path);
        when(relRoot.<Object>get("unit2")).thenReturn(unit2Path);
        when(cb.equal(unit1Path, root)).thenReturn(unit1Eq);
        when(cb.equal(unit2Path, root)).thenReturn(unit2Eq);
        when(cb.or(unit1Eq, unit2Eq)).thenReturn(orPredicate);
        when(subquery.where(orPredicate)).thenReturn(subquery);
        when(cb.desc(subquery)).thenReturn(order);
        when(cb.conjunction()).thenReturn(null);

        Specification<RecordingUnit> spec = RecordingUnitSpec.orderByRelationshipCount(Sort.Direction.DESC);
        spec.toPredicate(root, query, cb);

        verify(subquery).where(orPredicate);
        verify(query).orderBy(order);
        verify(cb).desc(subquery);
    }
}
