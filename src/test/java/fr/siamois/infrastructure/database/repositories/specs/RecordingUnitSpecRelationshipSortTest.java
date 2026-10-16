package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.SystemRelations;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private Root<RelationLink> linkRoot;
    @Mock
    private Path<Object> definitionPath;
    @Mock
    private Path<Object> codePath;
    @Mock
    private Path<Object> sourcePath;
    @Mock
    private Path<Object> targetPath;
    @Mock
    private jakarta.persistence.criteria.Expression<Long> countExpr;
    @Mock
    private Predicate ofRelation;
    @Mock
    private Predicate sourceEq;
    @Mock
    private Predicate targetEq;
    @Mock
    private Predicate orPredicate;
    @Mock
    private Order order;

    @Test
    void orderByRelationshipCount_countsTheStratigraphicLinksOfEitherEndAndOrdersByIt() {
        when(query.subquery(Long.class)).thenReturn(subquery);
        when(subquery.from(RelationLink.class)).thenReturn(linkRoot);
        when(subquery.select(countExpr)).thenReturn(subquery);
        when(cb.count(linkRoot)).thenReturn(countExpr);
        when(linkRoot.get("definition")).thenReturn(definitionPath);
        when(definitionPath.get("code")).thenReturn(codePath);
        when(codePath.in(SystemRelations.ORDER_CODE, SystemRelations.SYNCHRONOUS_CODE)).thenReturn(ofRelation);
        when(linkRoot.<Object>get("sourceRecordingUnit")).thenReturn(sourcePath);
        when(linkRoot.<Object>get("targetRecordingUnit")).thenReturn(targetPath);
        when(cb.equal(sourcePath, root)).thenReturn(sourceEq);
        when(cb.equal(targetPath, root)).thenReturn(targetEq);
        when(cb.or(sourceEq, targetEq)).thenReturn(orPredicate);
        when(cb.desc(subquery)).thenReturn(order);

        Specification<RecordingUnit> spec = RecordingUnitSpec.orderByRelationshipCount(Sort.Direction.DESC);
        spec.toPredicate(root, query, cb);

        // only the two platform relations, the unit at either end
        verify(subquery).where(ofRelation, orPredicate);
        verify(query).orderBy(order);
        verify(cb).desc(subquery);
    }
}
