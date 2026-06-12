package fr.siamois.domain.services.specimen;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.infrastructure.database.repositories.specs.SpecimenSpec;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.SpecimenMapper;
import fr.siamois.mapper.SpecimenSummaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecimenServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);


    @Mock
    private SpecimenRepository specimenRepository;

    @Mock
    private InstitutionMapper institutionMapper;

    @Mock
    private SpecimenMapper specimenMapper;

    @InjectMocks
    private SpecimenService specimenService;

    @Mock
    private RecordingUnitRepository recordingUnitRepository;

    @Mock
    private SpecimenSummaryMapper specimenSummaryMapper;




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
        RecordingUnitSummaryDTO recordingUnitDTO = new RecordingUnitSummaryDTO();
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
                institutionId, recordingUnitId, fullIdentifier,
                categoryIds, globalFilter, langCode, pageable))
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
                        institutionId, recordingUnitId, fullIdentifier,
                        categoryIds, globalFilter, langCode, pageable);

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

    @Test
    void testFindNextByActionUnit_ShouldReturnNextSpecimen() {
        // Arrange
        RecordingUnitSummaryDTO ruDTO = new RecordingUnitSummaryDTO();
        ruDTO.setId(1L);
        SpecimenDTO currentDTO = new SpecimenDTO();
        currentDTO.setCreationTime(NOW);

        Specimen nextSpecimen = new Specimen();
        SpecimenDTO nextDTO = new SpecimenDTO();

        when(specimenRepository.findFirstByRecordingUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(eq(1L), any()))
                .thenReturn(Optional.of(nextSpecimen));
        when(specimenMapper.convert(nextSpecimen)).thenReturn(nextDTO);

        // Act
        SpecimenDTO result = specimenService.findNextByActionUnit(ruDTO, currentDTO);

        // Assert
        assertNotNull(result);
        assertEquals(nextDTO, result);
        verify(specimenRepository, never()).findFirstByRecordingUnitIdOrderByCreationTimeAsc(anyLong());
    }

    @Test
    void testFindNextByActionUnit_ShouldWrapAroundWhenNoNext() {
        // Arrange
        RecordingUnitSummaryDTO ruDTO = new RecordingUnitSummaryDTO();
        ruDTO.setId(1L);
        SpecimenDTO currentDTO = new SpecimenDTO();
        currentDTO.setCreationTime(NOW);

        Specimen oldestSpecimen = new Specimen();
        SpecimenDTO oldestDTO = new SpecimenDTO();

        // No next specimen found
        when(specimenRepository.findFirstByRecordingUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(eq(1L), any()))
                .thenReturn(Optional.empty());
        // Return the oldest instead
        when(specimenRepository.findFirstByRecordingUnitIdOrderByCreationTimeAsc(1L))
                .thenReturn(Optional.of(oldestSpecimen));
        when(specimenMapper.convert(oldestSpecimen)).thenReturn(oldestDTO);

        // Act
        SpecimenDTO result = specimenService.findNextByActionUnit(ruDTO, currentDTO);

        // Assert
        assertEquals(oldestDTO, result);
    }

    @Test
    void testFindPreviousByActionUnit_ShouldReturnPreviousSpecimen() {
        // Arrange
        RecordingUnitSummaryDTO ruDTO = new RecordingUnitSummaryDTO();
        ruDTO.setId(1L);
        SpecimenDTO currentDTO = new SpecimenDTO();
        currentDTO.setCreationTime(NOW);

        Specimen prevSpecimen = new Specimen();
        SpecimenDTO prevDTO = new SpecimenDTO();

        when(specimenRepository.findFirstByRecordingUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(eq(1L), any()))
                .thenReturn(Optional.of(prevSpecimen));
        when(specimenMapper.convert(prevSpecimen)).thenReturn(prevDTO);

        // Act
        SpecimenDTO result = specimenService.findPreviousByActionUnit(ruDTO, currentDTO);

        // Assert
        assertNotNull(result);
        assertEquals(prevDTO, result);
    }

    @Test
    void testFindPreviousByActionUnit_ShouldWrapAroundWhenNoPrevious() {
        // Arrange
        RecordingUnitSummaryDTO ruDTO = new RecordingUnitSummaryDTO();
        ruDTO.setId(1L);
        SpecimenDTO currentDTO = new SpecimenDTO();

        Specimen mostRecentSpecimen = new Specimen();
        SpecimenDTO mostRecentDTO = new SpecimenDTO();

        when(specimenRepository.findFirstByRecordingUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(anyLong(), any()))
                .thenReturn(Optional.empty());
        when(specimenRepository.findFirstByRecordingUnitIdOrderByCreationTimeDesc(1L))
                .thenReturn(Optional.of(mostRecentSpecimen));
        when(specimenMapper.convert(mostRecentSpecimen)).thenReturn(mostRecentDTO);

        // Act
        SpecimenDTO result = specimenService.findPreviousByActionUnit(ruDTO, currentDTO);

        // Assert
        assertEquals(mostRecentDTO, result);
    }

    @Test
    void testToggleValidated_IncompleteToComplete() {
        // Arrange
        Long id = 1L;
        Specimen specimen = new Specimen();
        specimen.setValidated(ValidationStatus.INCOMPLETE);

        when(specimenRepository.findById(id)).thenReturn(Optional.of(specimen));
        when(specimenRepository.save(any(Specimen.class))).thenAnswer(i -> i.getArguments()[0]);
        when(specimenMapper.convert(any(Specimen.class))).thenReturn(new SpecimenDTO());

        // Act
        specimenService.toggleValidated(id);

        // Assert
        assertEquals(ValidationStatus.COMPLETE, specimen.getValidated());
        verify(specimenRepository).save(specimen);
    }

    @Test
    void testToggleValidated_CompleteToValidated() {
        // Arrange
        Long id = 1L;
        Specimen specimen = new Specimen();
        specimen.setValidated(ValidationStatus.COMPLETE);

        when(specimenRepository.findById(id)).thenReturn(Optional.of(specimen));
        when(specimenRepository.save(any(Specimen.class))).thenAnswer(i -> i.getArguments()[0]);
        when(specimenMapper.convert(any(Specimen.class))).thenReturn(new SpecimenDTO());

        // Act
        specimenService.toggleValidated(id);

        // Assert
        assertEquals(ValidationStatus.VALIDATED, specimen.getValidated());
    }

    @Test
    void testToggleValidated_ValidatedToIncomplete() {
        // Arrange
        Long id = 1L;
        Specimen specimen = new Specimen();
        specimen.setValidated(ValidationStatus.VALIDATED);

        when(specimenRepository.findById(id)).thenReturn(Optional.of(specimen));
        when(specimenRepository.save(any(Specimen.class))).thenAnswer(i -> i.getArguments()[0]);
        when(specimenMapper.convert(any(Specimen.class))).thenReturn(new SpecimenDTO());

        // Act
        specimenService.toggleValidated(id);

        // Assert
        assertEquals(ValidationStatus.INCOMPLETE, specimen.getValidated());
    }

    @Test
    void testToggleValidated_NotFound_ThrowsException() {
        // Arrange
        when(specimenRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ActionUnitNotFoundException.class, () -> specimenService.toggleValidated(99L));
    }

    @Test
    void save_WithPreExistingFullIdentifier_ShouldNotGenerateIdentifiers() {
        // Arrange
        SpecimenDTO dto = new SpecimenDTO();
        dto.setFullIdentifier("RU-101_SPEC-44");

        Specimen specimenEntity = new Specimen();
        specimenEntity.setId(null);

        when(specimenMapper.invertConvert(dto)).thenReturn(specimenEntity);
        when(specimenRepository.save(any(Specimen.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(specimenMapper.convert(any(Specimen.class))).thenReturn(dto);

        // Act
        SpecimenDTO result = specimenService.save(dto);

        // Assert
        assertNotNull(result);
        assertEquals("RU-101_SPEC-44", result.getFullIdentifier());
        verify(specimenRepository, never()).findMaxUsedIdentifierByRecordingUnit(anyLong());
    }

    @Test
    void save_WithExistingId_ShouldFetchManagedInstanceAndSyncOtherFields() {
        // Arrange
        SpecimenDTO dto = new SpecimenDTO();
        dto.setFullIdentifier("RU-12_SPEC-5");

        Specimen incomingSpecimen = new Specimen();
        incomingSpecimen.setId(9900L);
        incomingSpecimen.setDescription("Updated Description");

        Specimen managedSpecimen = new Specimen();
        managedSpecimen.setId(9900L);
        managedSpecimen.setDescription("Old Description");

        when(specimenMapper.invertConvert(dto)).thenReturn(incomingSpecimen);
        when(specimenRepository.findById(9900L)).thenReturn(Optional.of(managedSpecimen));
        when(specimenRepository.save(managedSpecimen)).thenReturn(managedSpecimen);
        when(specimenMapper.convert(managedSpecimen)).thenReturn(dto);

        // Act
        specimenService.save(dto);

        // Assert
        assertEquals("Updated Description", managedSpecimen.getDescription());
        verify(specimenRepository).findById(9900L);
        verify(specimenRepository).save(managedSpecimen);
    }

    @Test
    void save_ShouldSynchronizeConceptCollectionsCorrectly() {
        // Arrange
        SpecimenDTO dto = new SpecimenDTO();
        dto.setFullIdentifier("IDENT_SYNC");

        Concept gold = new Concept();
        gold.setId(101L);
        gold.setExternalId("101");
        Concept silver = new Concept();
        silver.setId(102L);
        silver.setExternalId("102");
        Concept bronze = new Concept();
        bronze.setId(103L);
        bronze.setExternalId("103");

        Specimen incomingSpecimen = new Specimen();
        incomingSpecimen.setId(1L);
        // User sends Gold and Silver
        incomingSpecimen.setMaterial(new java.util.HashSet<>(List.of(gold, silver)));

        Specimen managedSpecimen = new Specimen();
        managedSpecimen.setId(1L);
        // Database contains Silver and Bronze
        managedSpecimen.setMaterial(new java.util.HashSet<>(List.of(silver, bronze)));

        when(specimenMapper.invertConvert(dto)).thenReturn(incomingSpecimen);
        when(specimenRepository.findById(1L)).thenReturn(Optional.of(managedSpecimen));
        when(specimenRepository.save(managedSpecimen)).thenAnswer(i -> i.getArgument(0));
        when(specimenMapper.convert(any(Specimen.class))).thenReturn(dto);

        // Act
        specimenService.save(dto);

        // Assert
        // Silver is retained, Bronze pruned out, Gold merged in cleanly
        Set<Concept> results = managedSpecimen.getMaterial();
        assertTrue(results.contains(silver));
        assertTrue(results.contains(gold));
        assertFalse(results.contains(bronze));
        assertEquals(2, results.size());
    }

    @Test
    void save_ShouldUpdateParentChildBidirectionalRelationships() {
        // Arrange
        SpecimenDTO dto = new SpecimenDTO();
        dto.setFullIdentifier("IDENT_REL");

        // Set up the persistent entity currently in DB tracking an old parent relationship
        Specimen managedSpecimen = new Specimen();
        managedSpecimen.setId(10L);
        managedSpecimen.setFullIdentifier("CHILD_10");

        Specimen oldParent = new Specimen();
        oldParent.setId(100L);
        oldParent.setFullIdentifier("PARENT_100");
        oldParent.setChildren(new java.util.HashSet<>(List.of(managedSpecimen)));
        managedSpecimen.setParents(new java.util.HashSet<>(List.of(oldParent)));

        // Set up incoming payload asking to switch to a new parent reference
        Specimen incomingSpecimen = new Specimen();
        incomingSpecimen.setId(10L);
        incomingSpecimen.setFullIdentifier("CHILD_10");
        Specimen newParentRef = new Specimen();
        newParentRef.setId(200L);
        newParentRef.setFullIdentifier("PARENT_200");
        incomingSpecimen.setParents(new java.util.HashSet<>(List.of(newParentRef)));

        Specimen newParentManaged = new Specimen();
        newParentManaged.setId(200L);
        newParentManaged.setFullIdentifier("PARENT_200");
        newParentManaged.setChildren(new java.util.HashSet<>());

        when(specimenMapper.invertConvert(dto)).thenReturn(incomingSpecimen);
        when(specimenRepository.findById(10L)).thenReturn(Optional.of(managedSpecimen));
        when(specimenRepository.findById(200L)).thenReturn(Optional.of(newParentManaged));

        when(specimenRepository.save(any(Specimen.class))).thenAnswer(i -> i.getArgument(0));
        when(specimenMapper.convert(any(Specimen.class))).thenReturn(dto);

        // Act
        specimenService.save(dto);

        // Assert
        // Verify relationship broken down from old parent
        assertFalse(oldParent.getChildren().contains(managedSpecimen));
        // Verify cross-reference appended onto the incoming entity parent
        assertTrue(newParentManaged.getChildren().contains(managedSpecimen));
        // Verify internal tracking lists match sync targets
        assertTrue(managedSpecimen.getParents().contains(newParentManaged));
        assertFalse(managedSpecimen.getParents().contains(oldParent));

        verify(specimenRepository).save(oldParent);
        verify(specimenRepository).save(newParentManaged);
    }

    @Test
    void save_ShouldSynchronizeChildrenListsAndPruneDroppedOnes() {
        // Arrange
        SpecimenDTO dto = new SpecimenDTO();
        dto.setFullIdentifier("IDENT_CHILD_SYNC");

        Specimen managedSpecimen = new Specimen();
        managedSpecimen.setId(50L);
        managedSpecimen.setFullIdentifier("PARENT_50");

        Specimen oldChild = new Specimen();
        oldChild.setId(501L);
        oldChild.setFullIdentifier("CHILD_501");
        managedSpecimen.setChildren(new java.util.HashSet<>(List.of(oldChild)));

        Specimen incomingSpecimen = new Specimen();
        incomingSpecimen.setId(50L);
        incomingSpecimen.setFullIdentifier("PARENT_50");

        Specimen newChildRef = new Specimen();
        newChildRef.setId(502L);
        newChildRef.setFullIdentifier("CHILD_502");
        incomingSpecimen.setChildren(new java.util.HashSet<>(List.of(newChildRef)));

        Specimen newChildManaged = new Specimen();
        newChildManaged.setId(502L);
        newChildManaged.setFullIdentifier("CHILD_502");

        when(specimenMapper.invertConvert(dto)).thenReturn(incomingSpecimen);
        when(specimenRepository.findById(50L)).thenReturn(Optional.of(managedSpecimen));
        when(specimenRepository.findById(502L)).thenReturn(Optional.of(newChildManaged));

        when(specimenRepository.save(any(Specimen.class))).thenAnswer(i -> i.getArgument(0));
        when(specimenMapper.convert(any(Specimen.class))).thenReturn(dto);

        // Act
        specimenService.save(dto);

        // Assert
        assertFalse(managedSpecimen.getChildren().contains(oldChild));
        assertTrue(managedSpecimen.getChildren().contains(newChildManaged));
        assertEquals(1, managedSpecimen.getChildren().size());
        verify(specimenRepository).findById(502L);
    }

    // =====================================================================
    // searchSpecimen / searchSpecimenInRecordingUnit / prepareSpecs /
    // resolveAncestorClosure
    // =====================================================================

    @Nested
    class SearchAndPrepareSpecsTests {

        InstitutionDTO institution;
        RecordingUnitDTO recordingUnitDTO;
        Pageable pageable;
        Specimen specimen;
        SpecimenDTO specimenDTO;

        @BeforeEach
        void init() {
            institution = new InstitutionDTO();
            institution.setId(1L);

            recordingUnitDTO = new RecordingUnitDTO();
            recordingUnitDTO.setId(5L);

            pageable = PageRequest.of(0, 10);

            specimen = new Specimen();
            specimen.setId(100L);

            specimenDTO = new SpecimenDTO();
            specimenDTO.setId(100L);
        }

        // ------------------------------------------------------------------
        // searchSpecimen — rootOnly=false
        // ------------------------------------------------------------------

        @Test
        void searchSpecimen_noFilters_delegatesAndMapsPage() {
            FilterDTO filters = new FilterDTO(false);
            Page<Specimen> page = new PageImpl<>(List.of(specimen));

            when(specimenRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
            when(specimenMapper.convert(specimen)).thenReturn(specimenDTO);

            Page<SpecimenDTO> result = specimenService.searchSpecimen(institution, filters, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0)).isSameAs(specimenDTO);
            verify(specimenRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void searchSpecimen_emptyPage_returnsEmptyAndSkipsMapper() {
            FilterDTO filters = new FilterDTO(false);
            when(specimenRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<SpecimenDTO> result = specimenService.searchSpecimen(institution, filters, pageable);

            assertTrue(result.isEmpty());
            verifyNoInteractions(specimenMapper);
        }

        @Test
        void searchSpecimen_rootOnlyFalse_neverCallsListVariantOfFindAll() {
            FilterDTO filters = new FilterDTO(false);
            filters.add(SpecimenSpec.ACTION_UNIT_FILTER, List.of(2L), FilterDTO.FilterType.EQUAL);
            when(specimenRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of()));

            specimenService.searchSpecimen(institution, filters, pageable);

            // list variant (used only in closure resolution) must not be called
            verify(specimenRepository, never()).findAll(any(Specification.class));
        }

        // ------------------------------------------------------------------
        // searchSpecimenInRecordingUnit
        // ------------------------------------------------------------------

        @Test
        void searchSpecimenInRecordingUnit_happyPath_delegatesAndMapsPage() {
            FilterDTO filters = new FilterDTO(false);
            Page<Specimen> page = new PageImpl<>(List.of(specimen));

            when(specimenRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
            when(specimenMapper.convert(specimen)).thenReturn(specimenDTO);

            Page<SpecimenDTO> result = specimenService.searchSpecimenInRecordingUnit(
                    institution, recordingUnitDTO, filters, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0)).isSameAs(specimenDTO);
            verify(specimenRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void searchSpecimenInRecordingUnit_emptyPage_returnsEmpty() {
            FilterDTO filters = new FilterDTO(false);
            when(specimenRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<SpecimenDTO> result = specimenService.searchSpecimenInRecordingUnit(
                    institution, recordingUnitDTO, filters, pageable);

            assertTrue(result.isEmpty());
        }

        // ------------------------------------------------------------------
        // prepareSpecs — rootOnly=true, no user filters
        // ------------------------------------------------------------------

        @Test
        void prepareSpecs_rootOnlyTrue_noUserFilters_doesNotResolveClosureAndCallsPageVariant() {
            FilterDTO filters = new FilterDTO(true);
            // No user filters → hasUserFilters() == false
            Page<Specimen> page = new PageImpl<>(List.of(specimen));
            when(specimenRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
            when(specimenMapper.convert(specimen)).thenReturn(specimenDTO);

            specimenService.searchSpecimen(institution, filters, pageable);

            // list findAll (closure resolution) must NOT be called
            verify(specimenRepository, never()).findAll(any(Specification.class));
            verifyNoInteractions(recordingUnitRepository);
        }

        // ------------------------------------------------------------------
        // prepareSpecs — rootOnly=true, user filters, matches found
        // ------------------------------------------------------------------

        @Test
        void prepareSpecs_rootOnlyTrue_withUserFilters_matchesFound_setsClosureAndMatchIds() {
            FilterDTO filters = new FilterDTO(true);
            filters.add(SpecimenSpec.ACTION_UNIT_FILTER, List.of(2L), FilterDTO.FilterType.EQUAL);

            specimen.setId(100L);
            when(specimenRepository.findAll(any(Specification.class)))
                    .thenReturn(List.of(specimen));
            when(recordingUnitRepository.findAncestorClosure(new Long[]{100L}))
                    .thenReturn(List.of(100L, 200L));
            when(specimenRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(specimen)));
            when(specimenMapper.convert(specimen)).thenReturn(specimenDTO);

            specimenService.searchSpecimen(institution, filters, pageable);

            verify(specimenRepository).findAll(any(Specification.class));
            verify(recordingUnitRepository).findAncestorClosure(new Long[]{100L});
            assertThat(filters.getAncestorClosure()).isNotNull();
            assertEquals(Set.of(100L), filters.getMatchIds());
        }

        @Test
        void prepareSpecs_rootOnlyTrue_withUserFilters_matchesFound_closureStoredInFilters() {
            FilterDTO filters = new FilterDTO(true);
            filters.add(SpecimenSpec.ACTION_UNIT_FILTER, List.of(3L), FilterDTO.FilterType.EQUAL);

            Specimen s1 = new Specimen(); s1.setId(10L);
            Specimen s2 = new Specimen(); s2.setId(20L);

            when(specimenRepository.findAll(any(Specification.class)))
                    .thenReturn(List.of(s1, s2));
            when(recordingUnitRepository.findAncestorClosure(any(Long[].class)))
                    .thenReturn(List.of(10L, 20L, 30L));
            when(specimenRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of()));

            specimenService.searchSpecimen(institution, filters, pageable);

            assertThat(new HashSet<>(filters.getAncestorClosure()))
                    .isEqualTo(new HashSet<>(Set.of(10L, 20L, 30L)));
            assertThat(filters.getMatchIds()).isEqualTo(Set.of(10L, 20L));
        }

        // ------------------------------------------------------------------
        // prepareSpecs — rootOnly=true, user filters, no matches
        // ------------------------------------------------------------------

        @Test
        void prepareSpecs_rootOnlyTrue_withUserFilters_noMatches_emptyClosureNoAncestorCall() {
            FilterDTO filters = new FilterDTO(true);
            filters.add(SpecimenSpec.ACTION_UNIT_FILTER, List.of(99L), FilterDTO.FilterType.EQUAL);

            when(specimenRepository.findAll(any(Specification.class)))
                    .thenReturn(Collections.emptyList());
            when(specimenRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<SpecimenDTO> result = specimenService.searchSpecimen(institution, filters, pageable);

            verify(recordingUnitRepository, never()).findAncestorClosure(any());
            assertTrue(result.isEmpty());
            assertThat(filters.getAncestorClosure()).isNotNull();
            assertTrue(filters.getAncestorClosure().isEmpty());
        }

        // ------------------------------------------------------------------
        // resolveAncestorClosure — cached closure is reused
        // ------------------------------------------------------------------

        @Test
        void resolveAncestorClosure_whenClosureAlreadyCached_reusesItWithoutQueryingRepos() {
            FilterDTO filters = new FilterDTO(true);
            filters.add(SpecimenSpec.ACTION_UNIT_FILTER, List.of(2L), FilterDTO.FilterType.EQUAL);
            // Pre-populate closure so the cache branch is taken
            filters.setAncestorClosure(Set.of(55L, 66L));

            when(specimenRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of()));

            specimenService.searchSpecimen(institution, filters, pageable);

            // list-variant and ancestor-closure query must NOT be called
            verify(specimenRepository, never()).findAll(any(Specification.class));
            verifyNoInteractions(recordingUnitRepository);
        }
    }

}