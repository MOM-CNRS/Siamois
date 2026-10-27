package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The SQL is only ever built from the enum's own constants; what is worth pinning is how the rows and the parameters are handled. */
class RelationFieldRepositoryTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final Query query = mock(Query.class);
    private final RelationFieldRepository repository = new RelationFieldRepository();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
    }

    private static Object[] row(long owner, long target, String label, Long total) {
        return new Object[]{owner, target, label, "unit1", 5L, "ext", true, false, null, total};
    }

    @Test
    void previews_groupsRowsPerOwnerAndReadsTheTotals() {
        when(query.getResultList()).thenReturn(List.of(row(1, 10, "A", 3L), row(1, 11, "B", 3L), row(2, 20, "C", 1L)));

        Map<Long, RelationFieldRepository.Preview> out =
                repository.previews(RelationField.RECORDING_UNIT_POSTERIOR, List.of(1L, 2L), 2);

        assertThat(out.get(1L).total()).isEqualTo(3);
        assertThat(out.get(1L).rows()).extracting(RelationFieldRepository.Row::label).containsExactly("A", "B");
        RelationFieldRepository.Row first = out.get(1L).rows().get(0);
        assertThat(first.targetId()).isEqualTo(10);
        assertThat(first.role()).isEqualTo("unit1");
        assertThat(first.conceptId()).isEqualTo(5L);
        assertThat(first.conceptExternalId()).isEqualTo("ext");
        assertThat(first.asynchronous()).isTrue();
        assertThat(first.conceptDirection()).isFalse();
        assertThat(first.uncertain()).isNull();
        assertThat(out.get(2L).rows()).hasSize(1);
        verify(query).setParameter("limit", 2);
    }

    @Test
    void previews_limitZeroOnlyCounts() {
        when(query.getResultList()).thenReturn(Collections.singletonList(
                new Object[]{1L, 10L, "A", null, null, null, null, null, null, 4L}));

        Map<Long, RelationFieldRepository.Preview> out = repository.previews(RelationField.RECORDING_UNIT_FINDS, List.of(1L), 0);

        assertThat(out.get(1L).rows()).isEmpty();
        assertThat(out.get(1L).total()).isEqualTo(4);
        verify(query).setParameter("limit", 1);
    }

    @Test
    void previews_withoutOwnersDoesNotQuery() {
        assertThat(repository.previews(RelationField.RECORDING_UNIT_FINDS, List.of(), 3)).isEmpty();
        assertThat(repository.previews(RelationField.RECORDING_UNIT_FINDS, null, 3)).isEmpty();
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void page_bindsTheSearchAndTheWindow() {
        when(query.getResultList()).thenReturn(Collections.singletonList(new Object[]{1L, 10L, "A", null, null, null, null, null, null}));

        List<RelationFieldRepository.Row> rows =
                repository.page(RelationField.PHASE_RECORDING_UNITS, 1L, 20, 10, "  Mur ", false);

        assertThat(rows).hasSize(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).contains("lower(v.label) LIKE :search").contains("DESC NULLS LAST");
        verify(query).setParameter("search", "%mur%");
        verify(query).setFirstResult(20);
        verify(query).setMaxResults(10);
    }

    @Test
    void page_withoutSearchAscending() {
        when(query.getResultList()).thenReturn(List.of());

        assertThat(repository.page(RelationField.CONTAINER_FINDS, 1L, 0, 5, " ", true)).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).doesNotContain(":search").contains("ASC NULLS LAST");
        verify(query, never()).setParameter(eq("search"), any());
    }

    @Test
    void pageAcross_readsTheRelationsSideBySide_eachValueSayingWhichOneItIsFrom() {
        when(query.getResultList()).thenReturn(List.of(
                new Object[]{"parents", 0, 1L, 10L, "A", null, null, null, null, null, null},
                new Object[]{"anterior", 2, 1L, 11L, "B", "unit2", 5L, "ext", true, false, true}));
        Map<String, RelationField> kinds = new java.util.LinkedHashMap<>();
        kinds.put("parents", RelationField.RECORDING_UNIT_PARENTS);
        kinds.put("anterior", RelationField.RECORDING_UNIT_ANTERIOR);

        List<RelationFieldRepository.KindedRow> rows = repository.pageAcross(kinds, 1L, 20, 10, " US ", false, true);

        assertThat(rows).extracting(RelationFieldRepository.KindedRow::kind).containsExactly("parents", "anterior");
        assertThat(rows.get(1).row().targetId()).isEqualTo(11);
        assertThat(rows.get(1).row().label()).isEqualTo("B");
        assertThat(rows.get(1).row().role()).isEqualTo("unit2");
        assertThat(rows.get(1).row().uncertain()).isTrue();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).contains("'parents' AS kind, 0 AS kind_order").contains("'anterior' AS kind, 1 AS kind_order")
                .contains("UNION ALL").contains("lower(k.label) LIKE :search")
                .contains("ORDER BY k.kind_order ASC, lower(k.label) NULLS LAST");
        verify(query).setParameter("owner", 1L);
        verify(query).setParameter("search", "%us%");
        verify(query).setFirstResult(20);
        verify(query).setMaxResults(10);
    }

    @Test
    void pageAcross_sortsByLabelWhenAsked() {
        when(query.getResultList()).thenReturn(List.of());

        repository.pageAcross(Map.of("children", RelationField.RECORDING_UNIT_CHILDREN), 1L, 0, 5, null, true, false);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).contains("ORDER BY lower(k.label) DESC NULLS LAST, k.kind_order").doesNotContain(":search");
    }

    @Test
    void acrossNoRelationAtAll_nothingIsQueried() {
        assertThat(repository.pageAcross(Map.of(), 1L, 0, 5, null, false, true)).isEmpty();
        assertThat(repository.countAcross(Map.of(), 1L, null)).isZero();
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void countAcross_countsTheSameValuesThePageReads() {
        when(query.getSingleResult()).thenReturn(9L);

        assertThat(repository.countAcross(Map.of("parents", RelationField.RECORDING_UNIT_PARENTS), 1L, "x")).isEqualTo(9);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).startsWith("SELECT count(*)").contains("'parents' AS kind");
        verify(query).setParameter("search", "%x%");
    }

    @Test
    void count_readsASingleNumber() {
        when(query.getSingleResult()).thenReturn(7L);

        assertThat(repository.count(RelationField.RECORDING_UNIT_PARENTS, 1L, "x")).isEqualTo(7);
        verify(query).setParameter("search", "%x%");
    }

    @Test
    void relationFieldsAreLookedUpByOwnerAndBinding() {
        assertThat(RelationField.of(RecordingUnit.class, "parents")).contains(RelationField.RECORDING_UNIT_PARENTS);
        assertThat(RelationField.of(RecordingUnit.class, null)).isEmpty();
        assertThat(RelationField.isRelation(RecordingUnit.class, "nope")).isFalse();

        RelationField finds = RelationField.RECORDING_UNIT_FINDS;
        assertThat(finds.ownerType()).isEqualTo(RecordingUnit.class);
        assertThat(finds.valueBinding()).isEqualTo("specimenList");
        assertThat(finds.resourceType()).isEqualTo("finds");
        assertThat(finds.readOnly()).isTrue();
        assertThat(finds.qualified()).isFalse();
        assertThat(RelationField.RECORDING_UNIT_POSTERIOR.qualified()).isTrue();
        assertThat(RelationField.RECORDING_UNIT_ANTERIOR.stratigraphicPosition()).isEqualTo("anterior");
        assertThat(RelationField.RECORDING_UNIT_SYNCHRONOUS.stratigraphicPosition()).isEqualTo("synchronous");
        assertThat(RelationField.RECORDING_UNIT_PARENTS.stratigraphicPosition()).isNull();
        assertThat(RelationField.stratigraphic("posterior")).contains(RelationField.RECORDING_UNIT_POSTERIOR);
        assertThat(RelationField.stratigraphic("parents")).isEmpty();
        assertThat(RelationField.RECORDING_UNIT_PARENTS.resourceType()).isEqualTo("recording-units");
    }
}
