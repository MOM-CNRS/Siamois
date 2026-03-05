package fr.siamois.domain.services.specimen;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecimenServiceTest {

    @Mock
    private SpecimenRepository specimenRepository;

    private SpecimenService specimenService;

    private ConversionService conversionService;

    @BeforeEach
    void setUp() {
        specimenService = new SpecimenService(specimenRepository,conversionService);
    }


    @Test
    void findWithoutArk() {
        Institution institution = new Institution();
        institution.setId(1L);
        Specimen specimen = new Specimen();

        when(specimenRepository.findAllByArkIsNullAndCreatedByInstitution(institution))
                .thenReturn(List.of(specimen));

        List<? extends ArkEntity> result = specimenService.findWithoutArk(institution);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(specimenRepository, times(1))
                .findAllByArkIsNullAndCreatedByInstitution(institution);
    }

    @Test
    void save() {
        Specimen specimen = new Specimen();
        RecordingUnit ru = new RecordingUnit();
        ru.setFullIdentifier("test");
        specimen.setRecordingUnit(ru);
        SpecimenDTO specimenDTO = new SpecimenDTO();

        when(specimenRepository.save(specimen)).thenReturn(specimen);

        AbstractEntityDTO result = specimenService.save(specimenDTO);

        assertNotNull(result);
        assertEquals(specimenDTO, result);
        verify(specimenRepository, times(1)).save(specimen);
    }

    @Test
    void testFindById_found() {
        Specimen specimen = new Specimen();
        specimen.setId(123L);

        when(specimenRepository.findById(123L)).thenReturn(Optional.of(specimen));

        var result = specimenService.findById(123L);

        assertNotNull(result);
        assertEquals(123L, result.getId());
    }

    @Test
    void testFindById_notFound() {
        when(specimenRepository.findById(456L)).thenReturn(Optional.empty());

        var result = specimenService.findById(456L);

        assertNull(result);
    }

    @Test
    void testFindAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining() {
        Page<Specimen> expectedPage = new PageImpl<>(List.of(new Specimen()));
        when(specimenRepository.findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(1L), eq("ABC"), any(), eq("filter"), eq("en"), any(Pageable.class)))
                .thenReturn(expectedPage);

        var result = specimenService.findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                1L, "ABC", new Long[]{1L, 2L}, "filter", "en", PageRequest.of(0, 10));

        assertEquals(expectedPage, result);
    }

    @Test
    void testFindAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining() {
        Page<Specimen> expectedPage = new PageImpl<>(List.of(new Specimen()));
        when(specimenRepository.findAllByInstitutionAndByRecordingUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(1L), eq(10L), eq("DEF"), any(), eq("global"), eq("fr"), any(Pageable.class)))
                .thenReturn(expectedPage);

        var result = specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                1L, 10L, "DEF", new Long[]{3L}, "global", "fr", PageRequest.of(1, 5));

        assertEquals(expectedPage, result);
    }

    @Test
    void testBulkUpdateType() {
        ConceptDTO concept = new ConceptDTO();
        concept.setId(42L);

        when(specimenRepository.updateTypeByIds(eq(42L), anyList())).thenReturn(3);

        int updated = specimenService.bulkUpdateType(List.of(1L, 2L, 3L), concept);

        assertEquals(3, updated);
        verify(specimenRepository).updateTypeByIds(42L, List.of(1L, 2L, 3L));
    }

    @Test
    void testCountByInstitution() {
        InstitutionDTO institution = new InstitutionDTO();
        when(specimenRepository.countByCreatedByInstitution(any(Institution.class))).thenReturn(99L);

        long count = specimenService.countByInstitution(institution);

        assertEquals(99L, count);
    }

    @Test
    void test_findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining() {
        Page<Specimen> expectedPage = new PageImpl<>(List.of(new Specimen()));
        when(specimenRepository.findAllBySpatialUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(1L), eq("DEF"), any(), eq("global"), eq("fr"), any(Pageable.class)))
                .thenReturn(expectedPage);

        var result = specimenService.findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                1L, "DEF", new Long[]{3L}, "global", "fr", PageRequest.of(1, 5));

        assertEquals(expectedPage, result);
    }

    @Test
    void test_findAllByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining() {
        Page<Specimen> expectedPage = new PageImpl<>(List.of(new Specimen()));
        when(specimenRepository.findAllByActionUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(1L), eq("DEF"), any(), eq("global"), eq("fr"), any(Pageable.class)))
                .thenReturn(expectedPage);

        var result = specimenService.findAllByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                1L, "DEF", new Long[]{3L}, "global", "fr", PageRequest.of(1, 5));

        assertEquals(expectedPage, result);
    }



    @Test
    void testCountByActionContext() {
        // Arrange
        ActionUnitDTO actionUnit = mock(ActionUnitDTO.class);
        when(actionUnit.getId()).thenReturn(7L);
        when(specimenRepository.countByActionContext(7L)).thenReturn(3);

        // Act
        Integer result = specimenService.countByActionContext(actionUnit);

        // Assert
        assertEquals(3, result);
        verify(actionUnit).getId();
        verify(specimenRepository).countByActionContext(7L);
        verifyNoMoreInteractions(specimenRepository);
    }
}