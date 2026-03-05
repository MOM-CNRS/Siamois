package fr.siamois.domain.services.specimen;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.SpecimenMapper;
import io.swagger.models.auth.In;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecimenServiceTest {

    @Mock
    private SpecimenRepository specimenRepository;




    @Mock
    private InstitutionMapper institutionMapper;

    @Mock
    private SpecimenMapper specimenMapper;

    @InjectMocks
    private SpecimenService specimenService;


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
    void save_ShouldGenerateIdentifierAndSaveSpecimen() {
        // Préparation des données
        SpecimenDTO specimenDTO = new SpecimenDTO();
        RecordingUnitDTO recordingUnitDTO = new RecordingUnitDTO();
        recordingUnitDTO.setId(1L);
        recordingUnitDTO.setFullIdentifier("test");
        specimenDTO.setRecordingUnit(recordingUnitDTO);

        Specimen specimen = new Specimen();
        RecordingUnit recordingUnit = new RecordingUnit();
        recordingUnit.setId(1L);
        specimen.setRecordingUnit(recordingUnit);

        // Mock du repository pour la génération de l'identifiant
        when(specimenRepository.findMaxUsedIdentifierByRecordingUnit(1L)).thenReturn(5);

        // Mock du mapper
        when(specimenMapper.invertConvert(specimenDTO)).thenReturn(specimen);
        when(specimenRepository.save(specimen)).thenReturn(specimen);

        // Mock du DTO retourné
        SpecimenDTO savedSpecimenDTO = new SpecimenDTO();
        when(specimenMapper.convert(specimen)).thenReturn(savedSpecimenDTO);

        // Appel de la méthode
        AbstractEntityDTO result = specimenService.save(specimenDTO);

        // Vérifications
        assertNotNull(result);
        assertTrue(result instanceof SpecimenDTO);
        assertEquals(6, specimenDTO.getIdentifier());

        // Vérification des appels
        verify(specimenMapper, times(1)).invertConvert(specimenDTO);
        verify(specimenRepository, times(1)).save(specimen);
        verify(specimenMapper, times(1)).convert(specimen);
    }


    @Test
    void testFindById_found() {
        // Préparation des données
        Long specimenId = 123L;
        Specimen specimen = new Specimen();
        specimen.setId(specimenId);

        SpecimenDTO expectedDto = new SpecimenDTO();
        expectedDto.setId(specimenId);

        // Mock du repository
        when(specimenRepository.findById(specimenId, Specimen.class))
                .thenReturn(Optional.of(specimen));

        // Mock du mapper
        when(specimenMapper.convert(specimen))
                .thenReturn(expectedDto);

        // Appel de la méthode
        SpecimenDTO result = specimenService.findById(specimenId);

        // Vérifications
        assertNotNull(result);
        assertEquals(specimenId, result.getId());

        // Vérification des appels
        verify(specimenRepository, times(1))
                .findById(specimenId, Specimen.class);
        verify(specimenMapper, times(1))
                .convert(specimen);
    }


    @Test
    void testFindById_notFound() {
        // Préparation des données
        Long specimenId = 456L;

        // Mock du repository
        when(specimenRepository.findById(specimenId, Specimen.class))
                .thenReturn(Optional.empty());

        // Appel de la méthode
        SpecimenDTO result = specimenService.findById(specimenId);

        // Vérification
        assertNull(result);

        // Vérification des appels
        verify(specimenRepository, times(1))
                .findById(specimenId, Specimen.class);
    }


    @Test
    void testFindAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining() {
        // Préparation des données mockées
        Specimen mockSpecimen = new Specimen();
        SpecimenDTO mockSpecimenDTO = new SpecimenDTO();
        Page<Specimen> specimenPage = new PageImpl<>(List.of(mockSpecimen));

        // Mock du repository
        when(specimenRepository.findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(1L), eq("ABC"), eq(new Long[]{1L, 2L}), eq("filter"), eq("en"), any(Pageable.class)))
                .thenReturn(specimenPage);

        // Mock du mapper
        when(specimenMapper.convert(mockSpecimen)).thenReturn(mockSpecimenDTO);

        // Appel de la méthode
        Page<SpecimenDTO> result = specimenService.findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                1L, "ABC", new Long[]{1L, 2L}, "filter", "en", PageRequest.of(0, 10));

        // Vérifications
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(mockSpecimenDTO, result.getContent().get(0));

        // Vérification des appels
        verify(specimenRepository).findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(1L), eq("ABC"), eq(new Long[]{1L, 2L}), eq("filter"), eq("en"), any(Pageable.class));
    }


    @Test
    void testFindAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining() {
        // 1. Préparation des données
        final Long institutionId = 1L;
        final Long recordingUnitId = 10L;
        final String fullIdentifier = "DEF";
        final Long[] categoryIds = new Long[]{3L};
        final String globalFilter = "global";
        final String langCode = "fr";
        final Pageable pageable = PageRequest.of(1, 5);

        Specimen mockSpecimen = new Specimen();
        SpecimenDTO mockDto = new SpecimenDTO();
        Page<Specimen> mockPage = new PageImpl<>(Collections.singletonList(mockSpecimen));

        // 2. Configuration des mocks
        when(specimenRepository.findAllByInstitutionAndByRecordingUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(institutionId), eq(recordingUnitId), eq(fullIdentifier),
                eq(categoryIds), eq(globalFilter), eq(langCode), eq(pageable)))
                .thenReturn(mockPage);

        when(specimenMapper.convert(mockSpecimen))
                .thenReturn(mockDto);

        // 3. Exécution du test
        Page<SpecimenDTO> result = specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                institutionId, recordingUnitId, fullIdentifier, categoryIds, globalFilter, langCode, pageable);

        // 4. Assertions
        assertNotNull(result, "La page de résultats ne doit pas être null");
        assertEquals(1, result.getTotalElements(), "La page doit contenir exactement 1 élément");
        assertEquals(mockDto, result.getContent().get(0), "Le DTO retourné doit correspondre au mock");

        // 5. Vérification des interactions
        verify(specimenRepository, times(1))
                .findAllByInstitutionAndByRecordingUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                        eq(institutionId), eq(recordingUnitId), eq(fullIdentifier),
                        eq(categoryIds), eq(globalFilter), eq(langCode), eq(pageable));

        verify(specimenMapper, times(1))
                .convert(mockSpecimen);
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
        // 1. Préparation des données
        InstitutionDTO institutionDTO = new InstitutionDTO();
        institutionDTO.setId(1L); // Ajoutez d'autres propriétés si nécessaire

        Institution institutionEntity = new Institution();
        institutionEntity.setId(1L); // Doit correspondre à l'ID du DTO

        // 2. Configuration des mocks
        when(institutionMapper.invertConvert(institutionDTO))
                .thenReturn(institutionEntity);

        when(specimenRepository.countByCreatedByInstitution(institutionEntity))
                .thenReturn(99L);

        // 3. Exécution du test
        long count = specimenService.countByInstitution(institutionDTO);

        // 4. Assertions
        assertEquals(99L, count, "Le nombre de spécimens doit être 99");

        // 5. Vérification des interactions
        verify(institutionMapper, times(1))
                .invertConvert(institutionDTO);

        verify(specimenRepository, times(1))
                .countByCreatedByInstitution(institutionEntity);
    }


    @Test
    void test_findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining() {
        // 1. Préparation des données
        Specimen mockSpecimen = new Specimen();
        SpecimenDTO mockDto = new SpecimenDTO();
        Page<Specimen> mockPage = new PageImpl<>(List.of(mockSpecimen));

        // 2. Configuration des mocks
        when(specimenRepository.findAllBySpatialUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(1L), eq("DEF"), eq(new Long[]{3L}), eq("global"), eq("fr"), any(Pageable.class)))
                .thenReturn(mockPage);

        when(specimenMapper.convert(mockSpecimen))
                .thenReturn(mockDto);

        // 3. Exécution du test
        Page<SpecimenDTO> result = specimenService.findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                1L, "DEF", new Long[]{3L}, "global", "fr", PageRequest.of(1, 5));

        // 4. Assertions
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(mockDto, result.getContent().get(0));

        // 5. Vérification des interactions
        verify(specimenRepository, times(1))
                .findAllBySpatialUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                        eq(1L), eq("DEF"), eq(new Long[]{3L}), eq("global"), eq("fr"), any(Pageable.class));
        verify(specimenMapper, times(1))
                .convert(mockSpecimen);
    }

    @Test
    void test_findAllByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining() {
        // 1. Préparation des données
        Specimen mockSpecimen = new Specimen();
        SpecimenDTO mockDto = new SpecimenDTO();
        Page<Specimen> mockPage = new PageImpl<>(List.of(mockSpecimen));

        // 2. Configuration des mocks
        when(specimenRepository.findAllByActionUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(1L), eq("DEF"), eq(new Long[]{3L}), eq("global"), eq("fr"), any(Pageable.class)))
                .thenReturn(mockPage);

        when(specimenMapper.convert(mockSpecimen))
                .thenReturn(mockDto);

        // 3. Exécution du test
        Page<SpecimenDTO> result = specimenService.findAllByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                1L, "DEF", new Long[]{3L}, "global", "fr", PageRequest.of(1, 5));

        // 4. Assertions
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(mockDto, result.getContent().get(0));

        // 5. Vérification des interactions
        verify(specimenRepository, times(1))
                .findAllByActionUnitIdAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                        eq(1L), eq("DEF"), eq(new Long[]{3L}), eq("global"), eq("fr"), any(Pageable.class));
        verify(specimenMapper, times(1))
                .convert(mockSpecimen);
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