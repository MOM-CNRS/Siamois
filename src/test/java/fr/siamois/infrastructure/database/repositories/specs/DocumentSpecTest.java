package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.document.Document;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class DocumentSpecTest {

    private Root<Document> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Path<Object> path;
    private Predicate predicate;

    @BeforeEach
    void setUp() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        path = mock(Path.class);
        predicate = mock(Predicate.class);
        when(root.get(anyString())).thenReturn(path);
        when(path.get(anyString())).thenReturn(path);
        when(cb.equal(any(), any(Object.class))).thenReturn(predicate);
    }

    private Predicate apply(Specification<Document> spec) {
        return spec.toPredicate(root, (CriteriaQuery) query, cb);
    }

    @Test
    void belongsToInstitutionAndActionUnit_compareTheirIds() {
        assertThat(apply(DocumentSpec.belongsToInstitution(10L))).isSameAs(predicate);
        verify(cb).equal(path, 10L);
        assertThat(apply(DocumentSpec.belongsToActionUnit(7L))).isSameAs(predicate);
        verify(cb).equal(path, 7L);
    }

    @Test
    void withoutProject_isTheNullProjectPredicate() {
        when(cb.isNull(any())).thenReturn(predicate);

        assertThat(apply(DocumentSpec.withoutProject())).isSameAs(predicate);
        verify(root).get("actionUnit");
    }

    @Test
    void isInActionUnit_andIdIn_buildAnInClause() {
        CriteriaBuilder.In in = mock(CriteriaBuilder.In.class);
        when(cb.in(any(Expression.class))).thenReturn(in);
        when(in.value(any(Object.class))).thenReturn(in);
        when(path.in(any(java.util.Collection.class))).thenReturn(predicate);

        apply(DocumentSpec.isInActionUnit(List.of(7L)));
        assertThat(apply(DocumentSpec.idIn(List.of(1L)))).isSameAs(predicate);
    }

    @Test
    void identifierContaining_isCaseInsensitive_andIgnoresABlankValue() {
        Expression lower = mock(Expression.class);
        when(cb.lower(any(Expression.class))).thenReturn(lower);
        when(cb.like(any(Expression.class), anyString())).thenReturn(predicate);

        assertThat(apply(DocumentSpec.identifierContaining("DoC"))).isSameAs(predicate);
        verify(cb).like(lower, "%doc%");
        assertThat(apply(DocumentSpec.identifierContaining(" "))).isNull();
        assertThat(apply(DocumentSpec.identifierContaining(null))).isNull();
    }

    @Test
    void linkedTo_joinsTheMatchingCollection_andMakesTheQueryDistinct() {
        Join join = mock(Join.class);
        when(root.join(anyString(), eq(JoinType.INNER))).thenReturn(join);
        when(join.get("id")).thenReturn(path);

        apply(DocumentSpec.linkedToRecordingUnit(1L));
        apply(DocumentSpec.linkedToFind(2L));
        apply(DocumentSpec.linkedToPlace(3L));
        apply(DocumentSpec.linkedToPhase(4L));
        apply(DocumentSpec.linkedToContainer(5L));

        verify(root).join("recordingUnits", JoinType.INNER);
        verify(root).join("finds", JoinType.INNER);
        verify(root).join("places", JoinType.INNER);
        verify(root).join("phases", JoinType.INNER);
        verify(root).join("containers", JoinType.INNER);
        verify(query, org.mockito.Mockito.times(5)).distinct(true);
    }

    @Test
    void theClassIsNotInstantiable() throws Exception {
        var constructor = DocumentSpec.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
