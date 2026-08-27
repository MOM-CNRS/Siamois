package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.actionunit.ActionUnit;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.mockito.Mockito.verify;
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
}
