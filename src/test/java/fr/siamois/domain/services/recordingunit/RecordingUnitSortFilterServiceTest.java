package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.utils.context.ExecutionContextHolder;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class RecordingUnitSortFilterServiceTest {

    @Mock
    private RecordingUnitRepository recordingUnitRepository;

    @InjectMocks
    private RecordingUnitSortFilterService recordingUnitSortFilterService;

    @Test
    void userFilterSpecs_allColumnsSet_buildsSpecificationWithoutError() {
        FilterDTO filters = new FilterDTO(false);
        filters.add(RecordingUnitSpec.FULL_IDENTIFIER, "abc", FilterDTO.FilterType.CONTAINS);
        filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(1L), FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.MATRIX_FILTER, "clay", FilterDTO.FilterType.CONTAINS);
        filters.add(RecordingUnitSpec.SPATIAL_UNIT_FILTER, List.of(2L), FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.ACTION_UNIT_FILTER, List.of(3L), FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.CONTRIBUTORS_FILTER, List.of(4L), FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.TYPE_FILTER, List.of(5L), FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.NATURE_FILTER, List.of(7L), FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.AGENT_FILTER, List.of(8L), FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.INTERPRETATION_FILTER, List.of(9L), FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.OPENING_DATE_FILTER,
                List.of(java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now()),
                FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.CLOSING_DATE_FILTER,
                List.of(java.time.OffsetDateTime.now()), FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.TPQ_FILTER, java.util.Arrays.asList(-800, 1200), FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.TAQ_FILTER, java.util.Arrays.asList(null, 1500), FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.PARENTS_FILTER, List.of(6L), FilterDTO.FilterType.EQUAL);
        filters.add(RecordingUnitSpec.CHILDREN_FILTER, List.of(10L), FilterDTO.FilterType.EQUAL);

        Specification<RecordingUnit> spec = RecordingUnitSortFilterService.userFilterSpecs(filters);

        assertNotNull(spec);
    }

    @Test
    void prepareSpecs_rootOnlyNoUserFilters_returnsRootSpecWithoutRepositoryCall() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        FilterDTO filters = new FilterDTO(true);

        Specification<RecordingUnit> spec = recordingUnitSortFilterService.prepareSpecs(institution, filters);

        assertNotNull(spec);
        verifyNoInteractions(recordingUnitRepository);
    }

    @Test
    void prepareSpecs_rootOnlyWithUserFilters_emptyClosure_returnsDisjunctionSpec() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(9L), FilterDTO.FilterType.EQUAL);

        when(recordingUnitRepository.findAll(any(Specification.class))).thenReturn(List.of());

        Specification<RecordingUnit> spec = recordingUnitSortFilterService.prepareSpecs(institution, filters);

        assertNotNull(spec);
        verify(recordingUnitRepository).findAll(any(Specification.class));
        verify(recordingUnitRepository, never()).findAncestorClosure(any());
    }

    @Test
    void prepareSpecs_rootOnlyWithUserFilters_nonEmptyClosure_returnsRootAndClosureSpec() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(9L), FilterDTO.FilterType.EQUAL);

        RecordingUnit match = new RecordingUnit();
        match.setId(42L);
        match.setFullIdentifier("M42");
        when(recordingUnitRepository.findAll(any(Specification.class))).thenReturn(List.of(match));
        when(recordingUnitRepository.findAncestorClosure(new Long[]{42L})).thenReturn(List.of(42L, 1L));

        Specification<RecordingUnit> spec = recordingUnitSortFilterService.prepareSpecs(institution, filters);

        assertNotNull(spec);
    }

    @Test
    void prepareSpecs_notRootOnly_returnsUserFilterSpecWithoutRepositoryCall() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        FilterDTO filters = new FilterDTO(false);

        Specification<RecordingUnit> spec = recordingUnitSortFilterService.prepareSpecs(institution, filters);

        assertNotNull(spec);
        verifyNoInteractions(recordingUnitRepository);
    }

    @Test
    void computeAncestorClosure_notRootOnly_returnsEmptySet() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        FilterDTO filters = new FilterDTO(false);
        filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(9L), FilterDTO.FilterType.EQUAL);

        Set<Long> result = recordingUnitSortFilterService.computeAncestorClosure(institution, filters);

        assertTrue(result.isEmpty());
        verifyNoInteractions(recordingUnitRepository);
    }

    @Test
    void computeAncestorClosure_rootOnlyNoUserFilters_returnsEmptySet() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        FilterDTO filters = new FilterDTO(true);

        Set<Long> result = recordingUnitSortFilterService.computeAncestorClosure(institution, filters);

        assertTrue(result.isEmpty());
        verifyNoInteractions(recordingUnitRepository);
    }

    @Test
    void computeAncestorClosure_rootOnlyWithUserFilters_returnsResolvedClosure() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(9L), FilterDTO.FilterType.EQUAL);

        RecordingUnit match = new RecordingUnit();
        match.setId(42L);
        match.setFullIdentifier("M42");
        when(recordingUnitRepository.findAll(any(Specification.class))).thenReturn(List.of(match));
        when(recordingUnitRepository.findAncestorClosure(new Long[]{42L})).thenReturn(List.of(42L, 1L));

        Set<Long> result = recordingUnitSortFilterService.computeAncestorClosure(institution, filters);

        assertEquals(Set.of(42L, 1L), result);
    }


    @AfterEach
    void clearExecutionContext() {
        ExecutionContextHolder.clear();
    }

    // ------------------------------------------------------------------
    // applySyntheticSort / stripSyntheticSort
    // ------------------------------------------------------------------

    @Test
    void applySyntheticSort_countSortKey_composesAnOrderingOntoTheSpec() {
        Specification<RecordingUnit> base = Specification.where(null);

        Specification<RecordingUnit> ordered = recordingUnitSortFilterService.applySyntheticSort(
                base, Sort.by(Sort.Direction.DESC, RecordingUnitSpec.SPECIMEN_COUNT_SORT));

        assertNotSame(base, ordered);
    }

    @Test
    void applySyntheticSort_conceptLabelSortKey_composesAnOrderingOntoTheSpec() {
        Specification<RecordingUnit> base = Specification.where(null);

        Specification<RecordingUnit> ordered = recordingUnitSortFilterService.applySyntheticSort(
                base, Sort.by(Sort.Direction.ASC, RecordingUnitSpec.NATURE_LABEL_SORT));

        assertNotSame(base, ordered);
    }

    @Test
    void applySyntheticSort_plainJpaProperty_leavesTheSpecUntouched() {
        // tpq is a real column: Spring Data orders on it through the Pageable, not through a spec
        Specification<RecordingUnit> base = Specification.where(null);

        Specification<RecordingUnit> ordered = recordingUnitSortFilterService.applySyntheticSort(
                base, Sort.by(Sort.Direction.ASC, RecordingUnitSpec.TPQ_FILTER));

        assertSame(base, ordered);
    }

    @Test
    void applySyntheticSort_unsorted_leavesTheSpecUntouched() {
        Specification<RecordingUnit> base = Specification.where(null);

        Specification<RecordingUnit> ordered =
                recordingUnitSortFilterService.applySyntheticSort(base, Sort.unsorted());

        assertSame(base, ordered);
    }

    @Test
    void stripSyntheticSort_countSortKey_dropsTheSortAndKeepsThePaging() {
        Pageable pageable = PageRequest.of(2, 25,
                Sort.by(Sort.Direction.DESC, RecordingUnitSpec.CHILDREN_COUNT_SORT));

        Pageable stripped = recordingUnitSortFilterService.stripSyntheticSort(pageable);

        assertTrue(stripped.getSort().isUnsorted());
        assertEquals(2, stripped.getPageNumber());
        assertEquals(25, stripped.getPageSize());
    }

    @Test
    void stripSyntheticSort_conceptLabelSortKey_dropsTheSort() {
        Pageable pageable = PageRequest.of(0, 10,
                Sort.by(Sort.Direction.ASC, RecordingUnitSpec.INTERPRETATION_LABEL_SORT));

        Pageable stripped = recordingUnitSortFilterService.stripSyntheticSort(pageable);

        assertTrue(stripped.getSort().isUnsorted());
    }

    @Test
    void stripSyntheticSort_plainJpaProperty_returnsThePageableUnchanged() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, RecordingUnitSpec.TAQ_FILTER));

        assertSame(pageable, recordingUnitSortFilterService.stripSyntheticSort(pageable));
    }

    // ------------------------------------------------------------------
    // Alphabetical ordering reads the label in the current user's language
    // ------------------------------------------------------------------

    @Test
    void applySyntheticSort_conceptLabel_ordersOnTheContextLanguage() {
        ExecutionContextHolder.set(new UserInfo(new InstitutionDTO(), new PersonDTO(), "en"));

        assertEquals("en", langCodeOfConceptLabelOrdering(Sort.Direction.ASC));
    }

    @Test
    void applySyntheticSort_conceptLabel_noBoundContext_fallsBackToFrench() {
        ExecutionContextHolder.clear();

        assertEquals("fr", langCodeOfConceptLabelOrdering(Sort.Direction.ASC));
    }

    /**
     * Runs the composed ordering against mocked criteria objects and reports which language its
     * label subquery filters on.
     */
    @SuppressWarnings("rawtypes")
    private String langCodeOfConceptLabelOrdering(Sort.Direction direction) {
        Root<RecordingUnit> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Subquery<String> subquery = mock(Subquery.class);
        Root<ConceptPrefLabel> label = mock(Root.class);
        Path path = mock(Path.class);

        when(query.subquery(String.class)).thenReturn(subquery);
        when(subquery.from(ConceptPrefLabel.class)).thenReturn(label);
        when(label.get(anyString())).thenReturn(path);
        when(root.get(anyString())).thenReturn(path);

        recordingUnitSortFilterService
                .applySyntheticSort(Specification.where(null),
                        Sort.by(direction, RecordingUnitSpec.AGENT_LABEL_SORT))
                .toPredicate(root, query, cb);

        ArgumentCaptor<String> langCaptor = ArgumentCaptor.forClass(String.class);
        verify(cb).equal(any(Expression.class), langCaptor.capture());
        return langCaptor.getValue();
    }
}
