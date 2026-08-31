package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class RecordingUnitFilterServiceTest {

    @Mock
    private RecordingUnitRepository recordingUnitRepository;

    @InjectMocks
    private RecordingUnitFilterService recordingUnitFilterService;

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

        Specification<RecordingUnit> spec = RecordingUnitFilterService.userFilterSpecs(filters);

        assertNotNull(spec);
    }

    @Test
    void prepareSpecs_rootOnlyNoUserFilters_returnsRootSpecWithoutRepositoryCall() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        FilterDTO filters = new FilterDTO(true);

        Specification<RecordingUnit> spec = recordingUnitFilterService.prepareSpecs(institution, filters);

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

        Specification<RecordingUnit> spec = recordingUnitFilterService.prepareSpecs(institution, filters);

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

        Specification<RecordingUnit> spec = recordingUnitFilterService.prepareSpecs(institution, filters);

        assertNotNull(spec);
    }

    @Test
    void prepareSpecs_notRootOnly_returnsUserFilterSpecWithoutRepositoryCall() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        FilterDTO filters = new FilterDTO(false);

        Specification<RecordingUnit> spec = recordingUnitFilterService.prepareSpecs(institution, filters);

        assertNotNull(spec);
        verifyNoInteractions(recordingUnitRepository);
    }

    @Test
    void computeAncestorClosure_notRootOnly_returnsEmptySet() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        FilterDTO filters = new FilterDTO(false);
        filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(9L), FilterDTO.FilterType.EQUAL);

        Set<Long> result = recordingUnitFilterService.computeAncestorClosure(institution, filters);

        assertTrue(result.isEmpty());
        verifyNoInteractions(recordingUnitRepository);
    }

    @Test
    void computeAncestorClosure_rootOnlyNoUserFilters_returnsEmptySet() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        FilterDTO filters = new FilterDTO(true);

        Set<Long> result = recordingUnitFilterService.computeAncestorClosure(institution, filters);

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

        Set<Long> result = recordingUnitFilterService.computeAncestorClosure(institution, filters);

        assertEquals(Set.of(42L, 1L), result);
    }
}
