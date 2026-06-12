package fr.siamois.domain.services;

import fr.siamois.domain.models.phase.Phase;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.mapper.PhaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhaseServiceTest {

    @Mock
    private PhaseRepository phaseRepository;
    @Mock
    private PhaseMapper phaseMapper;

    @InjectMocks
    private PhaseService phaseService;

    private InstitutionDTO institution;
    private Pageable pageable;
    private Phase phase;
    private PhaseDTO phaseDTO;

    @BeforeEach
    void setUp() {
        institution = new InstitutionDTO();
        institution.setId(1L);

        pageable = PageRequest.of(0, 10);

        phase = new Phase();
        phase.setId(42L);
        phase.setIdentifier("P-01");

        phaseDTO = new PhaseDTO();
        phaseDTO.setId(42L);
        phaseDTO.setIdentifier("P-01");
    }

    // ------------------------------------------------------------------
    // searchPhases
    // ------------------------------------------------------------------

    @Test
    void searchPhases_noFilter_delegatesToRepositoryAndMapsResults() {
        FilterDTO filters = new FilterDTO(false);
        Page<Phase> repoPage = new PageImpl<>(List.of(phase));

        when(phaseRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);
        when(phaseMapper.convert(phase)).thenReturn(phaseDTO);

        Page<PhaseDTO> result = phaseService.searchPhases(institution, filters, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertSame(phaseDTO, result.getContent().get(0));
        verify(phaseRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchPhases_withNameFilter_stillDelegatesToRepository() {
        FilterDTO filters = new FilterDTO(false);
        filters.add(ActionUnitSpec.NAME_FILTER, "fouille", FilterDTO.FilterType.CONTAINS);
        Page<Phase> repoPage = new PageImpl<>(List.of(phase));

        when(phaseRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);
        when(phaseMapper.convert(phase)).thenReturn(phaseDTO);

        Page<PhaseDTO> result = phaseService.searchPhases(institution, filters, pageable);

        assertEquals(1, result.getTotalElements());
        verify(phaseRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchPhases_withGlobalFilter_stillDelegatesToRepository() {
        FilterDTO filters = new FilterDTO(false);
        filters.add(ActionUnitSpec.GLOBAL_FILTER, "fouille", FilterDTO.FilterType.CONTAINS);
        Page<Phase> repoPage = new PageImpl<>(List.of(phase));

        when(phaseRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(repoPage);
        when(phaseMapper.convert(phase)).thenReturn(phaseDTO);

        Page<PhaseDTO> result = phaseService.searchPhases(institution, filters, pageable);

        assertEquals(1, result.getTotalElements());
        verify(phaseRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchPhases_emptyPage_returnsEmptyPage() {
        FilterDTO filters = new FilterDTO(false);
        Page<Phase> emptyPage = new PageImpl<>(List.of());

        when(phaseRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

        Page<PhaseDTO> result = phaseService.searchPhases(institution, filters, pageable);

        assertTrue(result.isEmpty());
        verifyNoInteractions(phaseMapper);
    }

    // ------------------------------------------------------------------
    // countSearchResults
    // ------------------------------------------------------------------

    @Test
    void countSearchResults_noFilter_returnsRepositoryCount() {
        FilterDTO filters = new FilterDTO(false);
        when(phaseRepository.count(any(Specification.class))).thenReturn(5L);

        int count = phaseService.countSearchResults(institution, filters);

        assertEquals(5, count);
        verify(phaseRepository).count(any(Specification.class));
    }

    @Test
    void countSearchResults_withNameFilter_returnsRepositoryCount() {
        FilterDTO filters = new FilterDTO(false);
        filters.add(ActionUnitSpec.NAME_FILTER, "P", FilterDTO.FilterType.CONTAINS);
        when(phaseRepository.count(any(Specification.class))).thenReturn(3L);

        int count = phaseService.countSearchResults(institution, filters);

        assertEquals(3, count);
    }

    @Test
    void countSearchResults_zero_returnsZero() {
        FilterDTO filters = new FilterDTO(false);
        when(phaseRepository.count(any(Specification.class))).thenReturn(0L);

        assertEquals(0, phaseService.countSearchResults(institution, filters));
    }

    // ------------------------------------------------------------------
    // findById
    // ------------------------------------------------------------------

    @Test
    void findById_found_returnsMappedDTO() {
        when(phaseRepository.findById(42L)).thenReturn(Optional.of(phase));
        when(phaseMapper.convert(phase)).thenReturn(phaseDTO);

        PhaseDTO result = phaseService.findById(42L);

        assertNotNull(result);
        assertSame(phaseDTO, result);
    }

    @Test
    void findById_notFound_returnsNull() {
        when(phaseRepository.findById(99L)).thenReturn(Optional.empty());

        PhaseDTO result = phaseService.findById(99L);

        assertNull(result);
        verifyNoInteractions(phaseMapper);
    }

    // ------------------------------------------------------------------
    // save — new entity (no id)
    // ------------------------------------------------------------------

    @Test
    void save_newEntity_savesDirectlyAndReturnsMappedDTO() {
        PhaseDTO inputDTO = new PhaseDTO();
        // id is null → new entity

        Phase newEntity = new Phase();
        newEntity.setIdentifier("P-NEW");

        Phase saved = new Phase();
        saved.setId(100L);
        PhaseDTO savedDTO = new PhaseDTO();
        savedDTO.setId(100L);

        when(phaseMapper.invertConvert(inputDTO)).thenReturn(newEntity);
        when(phaseRepository.findById(-1L)).thenReturn(Optional.empty());
        when(phaseRepository.save(newEntity)).thenReturn(saved);
        when(phaseMapper.convert(saved)).thenReturn(savedDTO);

        PhaseDTO result = phaseService.save(inputDTO);

        assertSame(savedDTO, result);
        verify(phaseRepository).save(newEntity);
    }

    // ------------------------------------------------------------------
    // save — id provided but not found (treated as new)
    // ------------------------------------------------------------------

    @Test
    void save_idNotFoundInRepo_savesEntityDirectly() {
        PhaseDTO inputDTO = new PhaseDTO();
        inputDTO.setId(999L);

        Phase entity = new Phase();
        entity.setId(999L);

        Phase saved = new Phase();
        saved.setId(999L);
        PhaseDTO savedDTO = new PhaseDTO();
        savedDTO.setId(999L);

        when(phaseMapper.invertConvert(inputDTO)).thenReturn(entity);
        when(phaseRepository.findById(999L)).thenReturn(Optional.empty());
        when(phaseRepository.save(entity)).thenReturn(saved);
        when(phaseMapper.convert(saved)).thenReturn(savedDTO);

        PhaseDTO result = phaseService.save(inputDTO);

        assertSame(savedDTO, result);
        verify(phaseRepository).save(entity);
    }

    // ------------------------------------------------------------------
    // save — existing entity found → fields are merged onto managed
    // ------------------------------------------------------------------

    @Test
    void save_existingEntity_mergesFieldsOntoManagedAndSaves() {
        PhaseDTO inputDTO = new PhaseDTO();
        inputDTO.setId(42L);

        Phase entity = new Phase();
        entity.setId(42L);
        entity.setIdentifier("P-UPDATED");
        entity.setTitle("New title");
        entity.setDescription("New desc");
        entity.setOrderNumber(3);
        entity.setLowerBound(100);
        entity.setUpperBound(200);
        entity.setPeriods(new HashSet<>());
        entity.setKeywords(new HashSet<>());

        Phase managed = new Phase();    // different object — simulates existing DB row
        managed.setId(42L);
        managed.setIdentifier("P-OLD");
        managed.setPeriods(new HashSet<>());
        managed.setKeywords(new HashSet<>());

        Phase saved = new Phase();
        saved.setId(42L);
        PhaseDTO savedDTO = new PhaseDTO();
        savedDTO.setId(42L);

        when(phaseMapper.invertConvert(inputDTO)).thenReturn(entity);
        when(phaseRepository.findById(42L)).thenReturn(Optional.of(managed));
        when(phaseRepository.save(managed)).thenReturn(saved);
        when(phaseMapper.convert(saved)).thenReturn(savedDTO);

        PhaseDTO result = phaseService.save(inputDTO);

        assertSame(savedDTO, result);
        assertEquals("P-UPDATED", managed.getIdentifier());
        assertEquals("New title", managed.getTitle());
        assertEquals("New desc", managed.getDescription());
        assertEquals(3, managed.getOrderNumber());
        assertEquals(100, managed.getLowerBound());
        assertEquals(200, managed.getUpperBound());
        verify(phaseRepository).save(same(managed));
    }

    // ------------------------------------------------------------------
    // synchronizeCollection (tested via save)
    // ------------------------------------------------------------------

    @Test
    void save_incomingPeriodsNull_clearsManaged() {
        PhaseDTO inputDTO = new PhaseDTO();
        inputDTO.setId(5L);

        Phase entity = new Phase();
        entity.setId(5L);
        entity.setPeriods(null);   // incoming null → clear
        entity.setKeywords(new HashSet<>());

        Set<fr.siamois.domain.models.vocabulary.Concept> existingPeriods = new HashSet<>();
        existingPeriods.add(new fr.siamois.domain.models.vocabulary.Concept());

        Phase managed = new Phase();
        managed.setId(5L);
        managed.setPeriods(existingPeriods);
        managed.setKeywords(new HashSet<>());

        when(phaseMapper.invertConvert(inputDTO)).thenReturn(entity);
        when(phaseRepository.findById(5L)).thenReturn(Optional.of(managed));
        when(phaseRepository.save(managed)).thenReturn(managed);
        when(phaseMapper.convert(managed)).thenReturn(new PhaseDTO());

        phaseService.save(inputDTO);

        assertTrue(managed.getPeriods().isEmpty());
    }

    @Test
    void save_incomingPeriodsEmpty_clearsManaged() {
        PhaseDTO inputDTO = new PhaseDTO();
        inputDTO.setId(6L);

        Phase entity = new Phase();
        entity.setId(6L);
        entity.setPeriods(new HashSet<>());   // empty → clear
        entity.setKeywords(new HashSet<>());

        Set<fr.siamois.domain.models.vocabulary.Concept> existingPeriods = new HashSet<>();
        existingPeriods.add(new fr.siamois.domain.models.vocabulary.Concept());

        Phase managed = new Phase();
        managed.setId(6L);
        managed.setPeriods(existingPeriods);
        managed.setKeywords(new HashSet<>());

        when(phaseMapper.invertConvert(inputDTO)).thenReturn(entity);
        when(phaseRepository.findById(6L)).thenReturn(Optional.of(managed));
        when(phaseRepository.save(managed)).thenReturn(managed);
        when(phaseMapper.convert(managed)).thenReturn(new PhaseDTO());

        phaseService.save(inputDTO);

        assertTrue(managed.getPeriods().isEmpty());
    }

    @Test
    void save_incomingPeriodsMergeProperly() {
        PhaseDTO inputDTO = new PhaseDTO();
        inputDTO.setId(7L);

        // Concept.equals uses externalId + vocabulary — use distinct externalIds
        fr.siamois.domain.models.vocabulary.Concept kept = new fr.siamois.domain.models.vocabulary.Concept();
        kept.setExternalId("kept");
        fr.siamois.domain.models.vocabulary.Concept added = new fr.siamois.domain.models.vocabulary.Concept();
        added.setExternalId("added");
        fr.siamois.domain.models.vocabulary.Concept removed = new fr.siamois.domain.models.vocabulary.Concept();
        removed.setExternalId("removed");

        Phase entity = new Phase();
        entity.setId(7L);
        entity.setPeriods(new HashSet<>(Set.of(kept, added)));
        entity.setKeywords(new HashSet<>());

        Set<fr.siamois.domain.models.vocabulary.Concept> managedPeriods = new HashSet<>(Set.of(kept, removed));
        Phase managed = new Phase();
        managed.setId(7L);
        managed.setPeriods(managedPeriods);
        managed.setKeywords(new HashSet<>());

        when(phaseMapper.invertConvert(inputDTO)).thenReturn(entity);
        when(phaseRepository.findById(7L)).thenReturn(Optional.of(managed));
        when(phaseRepository.save(managed)).thenReturn(managed);
        when(phaseMapper.convert(managed)).thenReturn(new PhaseDTO());

        phaseService.save(inputDTO);

        assertTrue(managed.getPeriods().contains(kept));
        assertTrue(managed.getPeriods().contains(added));
        assertFalse(managed.getPeriods().contains(removed));
    }

    @Test
    void save_managedPeriodsNull_synchronizeIsSkipped() {
        PhaseDTO inputDTO = new PhaseDTO();
        inputDTO.setId(8L);

        fr.siamois.domain.models.vocabulary.Concept c = new fr.siamois.domain.models.vocabulary.Concept();
        c.setId(1L);

        Phase entity = new Phase();
        entity.setId(8L);
        entity.setPeriods(Set.of(c));
        entity.setKeywords(new HashSet<>());

        Phase managed = new Phase();
        managed.setId(8L);
        managed.setPeriods(null);   // managed collection is null → synchronize is no-op
        managed.setKeywords(new HashSet<>());

        when(phaseMapper.invertConvert(inputDTO)).thenReturn(entity);
        when(phaseRepository.findById(8L)).thenReturn(Optional.of(managed));
        when(phaseRepository.save(managed)).thenReturn(managed);
        when(phaseMapper.convert(managed)).thenReturn(new PhaseDTO());

        assertDoesNotThrow(() -> phaseService.save(inputDTO));
        assertNull(managed.getPeriods());
    }
}
