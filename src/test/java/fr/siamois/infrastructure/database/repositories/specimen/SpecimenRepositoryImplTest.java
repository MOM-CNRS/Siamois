package fr.siamois.infrastructure.database.repositories.specimen;

import fr.siamois.domain.models.specimen.Specimen;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecimenRepositoryImplTest {

  @Mock
  private EntityManager entityManager;

  private SpecimenRepositoryImpl repository;

  @BeforeEach
  void setUp() {
    repository = new SpecimenRepositoryImpl();
    ReflectionTestUtils.setField(repository, "entityManager", entityManager);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "s.creation_time DESC, s.specimen_id ASC",
      "s.creation_time ASC, s.specimen_id ASC",
      "s.full_identifier ASC, s.specimen_id ASC",
      "s.full_identifier DESC, s.specimen_id ASC",
      "s.specimen_id ASC",
      "s.specimen_id DESC",
      "c_label ASC, s.specimen_id ASC",
      "c_label DESC, s.specimen_id ASC",
      "unknown order by"
  })
  void findAll_usesWhitelistedOrderByAndBindsParameters(String orderByClause) {
    QueryMocks queries = stubEntityManagerQueries();
    doReturn(3L).when(queries.countQuery()).getSingleResult();
    Specimen specimen = new Specimen();
    doReturn(List.of(specimen)).when(queries.dataQuery()).getResultList();

    Page<Specimen> page = repository.findAllByInstitutionAndRecordingUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
        1L,
        2L,
        "RU-1",
        new Long[] {5L, 7L},
        "global",
        "fr",
        orderByClause,
        PageRequest.of(0, 5));

    assertThat(page.getContent()).containsExactly(specimen);
    assertThat(page.getPageable().getPageNumber()).isZero();
    assertThat(page.getPageable().getPageSize()).isEqualTo(5);

    var inOrder = inOrder(queries.countQuery(), queries.dataQuery());
    inOrder.verify(queries.countQuery()).getSingleResult();
    inOrder.verify(queries.dataQuery()).setFirstResult(0);
    inOrder.verify(queries.dataQuery()).setMaxResults(5);
    inOrder.verify(queries.dataQuery()).getResultList();
    verifyBindParams(queries.countQuery());
    verifyBindParams(queries.dataQuery());

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(entityManager).createNativeQuery(sqlCaptor.capture());
    verify(entityManager).createNativeQuery(sqlCaptor.capture(), eq(Specimen.class));
    assertThat(sqlCaptor.getAllValues().get(0)).contains("SELECT count(s)");
    assertThat(sqlCaptor.getAllValues().get(1)).contains("ORDER BY");
  }

  @Test
  void findAll_totalElementsComesFromCountQuery() {
    QueryMocks queries = stubEntityManagerQueries();
    when(queries.countQuery().getSingleResult()).thenReturn(3L);
    when(queries.dataQuery().getResultList()).thenReturn(List.of(new Specimen()));

    // pageSize must not exceed total: PageImpl adjusts total to offset + content.size()
    // when offset + pageSize > count (see Spring Data PageImpl constructor).
    PageRequest pageable = PageRequest.of(0, 3);

    Page<Specimen> page = repository.findAllByInstitutionAndRecordingUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
        1L, 2L, "RU-1", new Long[] {5L, 7L}, "global", "fr",
        "s.creation_time DESC, s.specimen_id ASC",
        pageable);

    verify(queries.countQuery()).getSingleResult();
    assertThat(page.getTotalElements()).isEqualTo(3L);
    assertThat(page.getNumberOfElements()).isEqualTo(1);
    assertThat(page.getPageable().getPageSize()).isEqualTo(3);
  }

  private QueryMocks stubEntityManagerQueries() {
    Query countQuery = mock(Query.class);
    Query dataQuery = mock(Query.class);
    when(entityManager.createNativeQuery(anyString(), eq(Specimen.class))).thenReturn(dataQuery);
    when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
      if (invocation.getArguments().length != 1) {
        throw new IllegalStateException("Unexpected createNativeQuery arity: " + invocation.getArguments().length);
      }
      String sql = invocation.getArgument(0, String.class);
      if (sql.contains("SELECT count(s)")) {
        return countQuery;
      }
      throw new IllegalStateException("Unexpected native count SQL: " + sql.substring(0, Math.min(120, sql.length())));
    });
    return new QueryMocks(countQuery, dataQuery);
  }

  private record QueryMocks(Query countQuery, Query dataQuery) {}

  private static void verifyBindParams(Query query) {
    verify(query).setParameter("institutionId", 1L);
    verify(query).setParameter("recordingUnitId", 2L);
    verify(query).setParameter("fullIdentifier", "RU-1");
    verify(query).setParameter("categoryIds", new Long[] {5L, 7L});
    verify(query).setParameter("global", "global");
    verify(query).setParameter("langCode", "fr");
  }

  @Test
  void findAll_paginatedSecondPage_appliesOffset() {
    QueryMocks queries = stubEntityManagerQueries();
    when(queries.countQuery().getSingleResult()).thenReturn(12L);
    when(queries.dataQuery().getResultList()).thenReturn(List.of());

    Page<Specimen> page = repository.findAllByInstitutionAndRecordingUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
        1L, 2L, null, null, null, "fr",
        "s.creation_time DESC, s.specimen_id ASC",
        PageRequest.of(2, 5));

    assertThat(page.getTotalElements()).isEqualTo(12L);
    verify(queries.dataQuery()).setFirstResult(10);
    verify(queries.dataQuery()).setMaxResults(5);
  }
}
