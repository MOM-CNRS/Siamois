package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationLink;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.mockito.Mockito.*;

/** Sorting recording units by their number of stratigraphic links: a correlated count over relation_link. */
class RecordingUnitSpecRelationshipSortTest {

    @Test
    @SuppressWarnings("unchecked")
    void orderByRelationshipCount_ordersByACountOfTheUnitsLinks() {
        Root<RecordingUnit> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<Object> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        Subquery<Long> subquery = mock(Subquery.class, RETURNS_DEEP_STUBS);
        Root<RelationLink> links = mock(Root.class, RETURNS_DEEP_STUBS);
        Order order = mock(Order.class);
        when(query.subquery(Long.class)).thenReturn(subquery);
        when(subquery.from(RelationLink.class)).thenReturn(links);
        when(cb.desc(subquery)).thenReturn(order);

        RecordingUnitSpec.orderByRelationshipCount(Sort.Direction.DESC).toPredicate(root, query, cb);

        verify(subquery).from(RelationLink.class);
        verify(query).orderBy(order);
    }
}
