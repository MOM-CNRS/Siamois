package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import jakarta.persistence.criteria.*;
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
class RecordingUnitSpecSortTest {

    @Mock
    private Root<RecordingUnit> root;
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
    void orderBySpecimenCount_ascending_setsAscOrderOnQuery() {
        when(root.<List<?>>get("specimenList")).thenReturn(collectionPath);
        when(cb.size(collectionPath)).thenReturn(sizeExpr);
        when(cb.asc(sizeExpr)).thenReturn(order);
        when(cb.conjunction()).thenReturn(null);

        Specification<RecordingUnit> spec = RecordingUnitSpec.orderBySpecimenCount(Sort.Direction.ASC);
        spec.toPredicate(root, query, cb);

        verify(query).orderBy(order);
        verify(cb).asc(sizeExpr);
    }

    @Test
    void orderBySpecimenCount_descending_setsDescOrderOnQuery() {
        when(root.<List<?>>get("specimenList")).thenReturn(collectionPath);
        when(cb.size(collectionPath)).thenReturn(sizeExpr);
        when(cb.desc(sizeExpr)).thenReturn(order);
        when(cb.conjunction()).thenReturn(null);

        Specification<RecordingUnit> spec = RecordingUnitSpec.orderBySpecimenCount(Sort.Direction.DESC);
        spec.toPredicate(root, query, cb);

        verify(query).orderBy(order);
        verify(cb).desc(sizeExpr);
    }


    @Test
    void orderByParentsCount_ascending_setsAscOrderOnQuery() {
        when(root.<List<?>>get(RecordingUnitSpec.PARENTS_FILTER)).thenReturn(collectionPath);
        when(cb.size(collectionPath)).thenReturn(sizeExpr);
        when(cb.asc(sizeExpr)).thenReturn(order);
        when(cb.conjunction()).thenReturn(null);

        Specification<RecordingUnit> spec = RecordingUnitSpec.orderByParentsCount(Sort.Direction.ASC);
        spec.toPredicate(root, query, cb);

        verify(query).orderBy(order);
        verify(cb).asc(sizeExpr);
    }

    @Test
    void orderByParentsCount_descending_setsDescOrderOnQuery() {
        when(root.<List<?>>get(RecordingUnitSpec.PARENTS_FILTER)).thenReturn(collectionPath);
        when(cb.size(collectionPath)).thenReturn(sizeExpr);
        when(cb.desc(sizeExpr)).thenReturn(order);
        when(cb.conjunction()).thenReturn(null);

        Specification<RecordingUnit> spec = RecordingUnitSpec.orderByParentsCount(Sort.Direction.DESC);
        spec.toPredicate(root, query, cb);

        verify(query).orderBy(order);
        verify(cb).desc(sizeExpr);
    }

    @Test
    void orderByChildrenCount_ascending_setsAscOrderOnQuery() {
        when(root.<List<?>>get(RecordingUnitSpec.CHILDREN_FILTER)).thenReturn(collectionPath);
        when(cb.size(collectionPath)).thenReturn(sizeExpr);
        when(cb.asc(sizeExpr)).thenReturn(order);
        when(cb.conjunction()).thenReturn(null);

        Specification<RecordingUnit> spec = RecordingUnitSpec.orderByChildrenCount(Sort.Direction.ASC);
        spec.toPredicate(root, query, cb);

        verify(query).orderBy(order);
        verify(cb).asc(sizeExpr);
    }

    @Test
    void orderByChildrenCount_descending_setsDescOrderOnQuery() {
        when(root.<List<?>>get(RecordingUnitSpec.CHILDREN_FILTER)).thenReturn(collectionPath);
        when(cb.size(collectionPath)).thenReturn(sizeExpr);
        when(cb.desc(sizeExpr)).thenReturn(order);
        when(cb.conjunction()).thenReturn(null);

        Specification<RecordingUnit> spec = RecordingUnitSpec.orderByChildrenCount(Sort.Direction.DESC);
        spec.toPredicate(root, query, cb);

        verify(query).orderBy(order);
        verify(cb).desc(sizeExpr);
    }
}
