package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock-based, mirroring {@link ActionUnitSpecTest}'s own style — this codebase has no
 * {@code @DataJpaTest}/integration-test infrastructure (no Testcontainers, H2 unused for tests),
 * so a real "does the count query see the right row count" assertion isn't available here. What
 * these tests establish instead is exactly the structural property the `distinct`-inflation trap
 * (plan §3 phase 3) depends on: {@link #conceptManyIn_usesExistsSubquery_neverDistinct} asserts
 * {@code query.distinct(...)} and {@code root.join(...)} are never called — the EXISTS-subquery
 * shape the code is supposed to take, not the join+distinct shape that inflates totalCount.
 */
@ExtendWith(MockitoExtension.class)
class ActionUnitFilterSpecTest {

    @Mock
    private Root<ActionUnit> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;

    @Test
    @SuppressWarnings("unchecked")
    void textContains_buildsCaseInsensitiveLikeWithCoalesce() {
        Path<Object> property = mock(Path.class);
        Expression<Object> coalesced = mock(Expression.class);
        Expression<String> lowered = mock(Expression.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("oaCode")).thenReturn(property);
        when(cb.coalesce(eq(property), eq(""))).thenReturn(coalesced);
        when(cb.lower(any())).thenReturn(lowered);
        when(cb.like(lowered, "%foss%")).thenReturn(predicate);

        Specification<ActionUnit> spec = ActionUnitFilterSpec.textContains("oaCode", "FOSS");
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void conceptOneIn_buildsIdInPredicate() {
        Path<Object> property = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("status")).thenReturn(property);
        when(property.get("id")).thenReturn(idPath);
        when(idPath.in(List.of(12L, 44L))).thenReturn(predicate);

        Specification<ActionUnit> spec = ActionUnitFilterSpec.conceptOneIn("status", List.of(12L, 44L));
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void conceptOneIn_emptyIds_isANoOpConjunction() {
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Specification<ActionUnit> spec = ActionUnitFilterSpec.conceptOneIn("status", List.of());
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(conjunction);
        verify(root, never()).get(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void spatialOneIn_buildsIdInPredicate() {
        Path<Object> property = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("mainLocation")).thenReturn(property);
        when(property.get("id")).thenReturn(idPath);
        when(idPath.in(List.of(9L))).thenReturn(predicate);

        Specification<ActionUnit> spec = ActionUnitFilterSpec.spatialOneIn("mainLocation", List.of(9L));
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void numericRange_bothBounds_usesBetween() {
        Path<Double> property = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.<Double>get("openingRate")).thenReturn(property);
        when(cb.between(eq(property), eq(10.0), eq(40.0))).thenReturn(predicate);

        Specification<ActionUnit> spec = ActionUnitFilterSpec.numericRange(
                "openingRate", new ProjectListFilter.NumericRange(10.0, 40.0));
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void numericRange_fromOnly_usesGreaterOrEqual() {
        Path<Double> property = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.<Double>get("openingRate")).thenReturn(property);
        when(cb.ge(eq(property), eq(10.0))).thenReturn(predicate);

        Specification<ActionUnit> spec = ActionUnitFilterSpec.numericRange(
                "openingRate", new ProjectListFilter.NumericRange(10.0, null));
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void numericRange_toOnly_usesLessOrEqual() {
        Path<Double> property = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.<Double>get("openingRate")).thenReturn(property);
        when(cb.le(eq(property), eq(40.0))).thenReturn(predicate);

        Specification<ActionUnit> spec = ActionUnitFilterSpec.numericRange(
                "openingRate", new ProjectListFilter.NumericRange(null, 40.0));
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void numericRange_neitherBound_isANoOpConjunction() {
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Specification<ActionUnit> spec = ActionUnitFilterSpec.numericRange(
                "openingRate", new ProjectListFilter.NumericRange(null, null));
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(conjunction);
    }

    /**
     * The load-bearing test in this file: asserts the EXISTS-subquery shape, and — the actual
     * regression this guards against — that neither {@code query.distinct(...)} nor
     * {@code root.join(...)} is ever invoked. A join+distinct implementation of a @ManyToMany
     * filter changes the row shape the paired count query sees too
     * ({@code findAll(spec, pageable)} issues one), silently inflating {@code totalCount} whenever
     * a project matches more than one selected period/subject.
     */
    @Test
    @SuppressWarnings("unchecked")
    void conceptManyIn_usesExistsSubquery_neverDistinct() {
        Subquery<Long> subquery = mock(Subquery.class);
        Root<ActionUnit> subRoot = mock(Root.class);
        Join<ActionUnit, Concept> conceptJoin = mock(Join.class);
        Path<Object> subRootId = mock(Path.class);
        Path<Object> rootId = mock(Path.class);
        Path<Object> conceptId = mock(Path.class);
        Expression<Long> literal = mock(Expression.class);
        Predicate equalsPredicate = mock(Predicate.class);
        Predicate inPredicate = mock(Predicate.class);
        Predicate existsPredicate = mock(Predicate.class);

        when(query.subquery(Long.class)).thenReturn(subquery);
        when(subquery.from(ActionUnit.class)).thenReturn(subRoot);
        when(subRoot.<ActionUnit, Concept>join("periods")).thenReturn(conceptJoin);
        when(cb.literal(1L)).thenReturn(literal);
        when(subquery.select(literal)).thenReturn(subquery);
        when(subRoot.get("id")).thenReturn(subRootId);
        when(root.get("id")).thenReturn(rootId);
        when(cb.equal(subRootId, rootId)).thenReturn(equalsPredicate);
        when(conceptJoin.get("id")).thenReturn(conceptId);
        when(conceptId.in(List.of(1L, 2L))).thenReturn(inPredicate);
        when(subquery.where(equalsPredicate, inPredicate)).thenReturn(subquery);
        when(cb.exists(subquery)).thenReturn(existsPredicate);

        Specification<ActionUnit> spec = ActionUnitFilterSpec.conceptManyIn("periods", List.of(1L, 2L));
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(existsPredicate);
        verify(query, never()).distinct(anyBoolean());
        verify(root, never()).join(anyString());
    }

    @Test
    void conceptManyIn_emptyIds_isANoOpConjunction() {
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Specification<ActionUnit> spec = ActionUnitFilterSpec.conceptManyIn("periods", List.of());
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(conjunction);
        verify(query, never()).subquery(any());
    }

    // fromFilter's own job is to iterate all five of ProjectListFilter's maps and delegate to the
    // builder above — with exactly one map populated, Spring's Specification.and() composition
    // returns that single predicate directly (its lhs, Specification.where(null), yields a null
    // predicate, so no cb.and(...) call happens at all), which keeps these wiring tests free of
    // the CriteriaBuilder overload-resolution fragility a multi-filter composition would need.

    @Test
    @SuppressWarnings("unchecked")
    void fromFilter_appliesAContainsFilterWhenPresent() {
        Path<Object> property = mock(Path.class);
        Expression<Object> coalesced = mock(Expression.class);
        Expression<String> lowered = mock(Expression.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("name")).thenReturn(property);
        when(cb.coalesce(eq(property), eq(""))).thenReturn(coalesced);
        when(cb.lower(any())).thenReturn(lowered);
        when(cb.like(lowered, "%foss%")).thenReturn(predicate);

        ProjectListFilter filter = new ProjectListFilter(
                java.util.Map.of("name", "foss"), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());

        assertThat(ActionUnitFilterSpec.fromFilter(filter).toPredicate(root, query, cb)).isSameAs(predicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fromFilter_appliesAConceptOneInFilterWhenPresent() {
        Path<Object> property = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("status")).thenReturn(property);
        when(property.get("id")).thenReturn(idPath);
        when(idPath.in(List.of(12L))).thenReturn(predicate);

        ProjectListFilter filter = new ProjectListFilter(
                java.util.Map.of(), java.util.Map.of("status", List.of(12L)), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());

        assertThat(ActionUnitFilterSpec.fromFilter(filter).toPredicate(root, query, cb)).isSameAs(predicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fromFilter_appliesAConceptManyInFilterWhenPresent() {
        Subquery<Long> subquery = mock(Subquery.class);
        Root<ActionUnit> subRoot = mock(Root.class);
        Join<ActionUnit, Concept> conceptJoin = mock(Join.class);
        Path<Object> subRootId = mock(Path.class);
        Path<Object> rootId = mock(Path.class);
        Path<Object> conceptId = mock(Path.class);
        Predicate existsPredicate = mock(Predicate.class);
        when(query.subquery(Long.class)).thenReturn(subquery);
        when(subquery.from(ActionUnit.class)).thenReturn(subRoot);
        when(subRoot.<ActionUnit, Concept>join("periods")).thenReturn(conceptJoin);
        when(subquery.select(any())).thenReturn(subquery);
        when(subRoot.get("id")).thenReturn(subRootId);
        when(root.get("id")).thenReturn(rootId);
        when(cb.equal(subRootId, rootId)).thenReturn(mock(Predicate.class));
        when(conceptJoin.get("id")).thenReturn(conceptId);
        when(conceptId.in(List.of(1L))).thenReturn(mock(Predicate.class));
        when(subquery.where(any(Predicate.class), any(Predicate.class))).thenReturn(subquery);
        when(cb.exists(subquery)).thenReturn(existsPredicate);

        ProjectListFilter filter = new ProjectListFilter(
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of("periods", List.of(1L)), java.util.Map.of(), java.util.Map.of());

        assertThat(ActionUnitFilterSpec.fromFilter(filter).toPredicate(root, query, cb)).isSameAs(existsPredicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fromFilter_appliesASpatialOneInFilterWhenPresent() {
        Path<Object> property = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("mainLocation")).thenReturn(property);
        when(property.get("id")).thenReturn(idPath);
        when(idPath.in(List.of(9L))).thenReturn(predicate);

        ProjectListFilter filter = new ProjectListFilter(
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of("mainLocation", List.of(9L)), java.util.Map.of());

        assertThat(ActionUnitFilterSpec.fromFilter(filter).toPredicate(root, query, cb)).isSameAs(predicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fromFilter_appliesANumericRangeFilterWhenPresent() {
        Path<Double> property = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.<Double>get("openingRate")).thenReturn(property);
        when(cb.ge(eq(property), eq(10.0))).thenReturn(predicate);

        ProjectListFilter filter = new ProjectListFilter(
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                java.util.Map.of("openingRate", new ProjectListFilter.NumericRange(10.0, null)));

        assertThat(ActionUnitFilterSpec.fromFilter(filter).toPredicate(root, query, cb)).isSameAs(predicate);
    }

    @Test
    void fromFilter_emptyFilter_yieldsNoPredicate() {
        assertThat(ActionUnitFilterSpec.fromFilter(ProjectListFilter.EMPTY).toPredicate(root, query, cb)).isNull();
    }
}
