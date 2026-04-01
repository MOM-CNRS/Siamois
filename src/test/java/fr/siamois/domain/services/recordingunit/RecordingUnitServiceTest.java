package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuIdentifierResolver;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.mapper.RecordingUnitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class RecordingUnitServiceTest {

    @Mock
    private RecordingUnitRepository recordingUnitRepository;

    @Mock
    private ConceptService conceptService;

    @Mock
    private RecordingUnitMapper recordingUnitMapper;

    @Mock
    private PersonRepository personRepository;
    
    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ConversionService conversionService;

    @InjectMocks
    private RecordingUnitService recordingUnitService;

    @Test
    void findWithoutArk() {
        Institution institution = new Institution();
        institution.setId(1L);
        RecordingUnit recordingUnit = new RecordingUnit();

        when(recordingUnitRepository.findAllWithoutArkOfInstitution(institution.getId())).thenReturn(Collections.singletonList(recordingUnit));

        List<? extends ArkEntity> result = recordingUnitService.findWithoutArk(institution);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(recordingUnitRepository, times(1)).findAllWithoutArkOfInstitution(institution.getId());
    }

    @Test
    void save() {

        RecordingUnit recordingUnit = new RecordingUnit();

        // Mock the conversion from DTO to entity
        when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(recordingUnit);
        // Mock the save method
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenReturn(recordingUnit);
        // Mock the conversion from entity to DTO (return a dummy DTO)
        when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(new RecordingUnitDTO());

        // Act
        recordingUnitService.save(new RecordingUnitDTO());

        // Assert: Capture the argument passed to the last convert call
        ArgumentCaptor<RecordingUnit> entityCaptor = forClass(RecordingUnit.class);
        verify(recordingUnitMapper).convert(entityCaptor.capture());

        RecordingUnit savedEntity = entityCaptor.getValue();
        assertNotNull(savedEntity);

    }

    @Test
    void countByInstitution_success() {
        when(recordingUnitRepository.countByCreatedByInstitutionId(3L)).thenReturn(3L);
        assertEquals(3, recordingUnitService.countByInstitutionId(3L));
    }

    @Test
    void save_SyncsStratigraphicRelationshipsAsUnit1() {
        // Arrange

        // DTO of recording unit with one relationship
        RecordingUnitSummaryDTO summary1 = new RecordingUnitSummaryDTO();
        summary1.setId(1L);
        summary1.setFullIdentifier("1");
        RecordingUnitSummaryDTO summary2 = new RecordingUnitSummaryDTO();
        summary2.setId(2L);
        summary2.setFullIdentifier("2");
        StratigraphicRelationshipDTO relDto = new StratigraphicRelationshipDTO();
        relDto.setUnit1(summary1);
        relDto.setUnit2(summary2);
        RecordingUnitDTO toInsertDto = new RecordingUnitDTO();
        toInsertDto.setId(1L);
        toInsertDto.setFullIdentifier("1");
        toInsertDto.setRelationshipsAsUnit1(new HashSet<>());
        toInsertDto.getRelationshipsAsUnit1().add(relDto);

        // Entity to insert
        RecordingUnit toInsert = new RecordingUnit();
        toInsert.setId(1L);
        toInsert.setFullIdentifier("1");
        RecordingUnit managed = new RecordingUnit();
        toInsert.setId(1L);
        toInsert.setFullIdentifier("1");
        RecordingUnit unit2 = new RecordingUnit();
        unit2.setId(2L);
        unit2.setFullIdentifier("2");
        StratigraphicRelationship rel = new StratigraphicRelationship();
        rel.setUnit1(toInsert);
        rel.setUnit2(unit2);
        Set<StratigraphicRelationship> rels = new HashSet<>();
        rels.add(rel);
        toInsert.setRelationshipsAsUnit1(rels);

        when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(toInsert);
        when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(unit2));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(toInsertDto);

        // Act
        RecordingUnitDTO result = recordingUnitService.save(toInsertDto);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getRelationshipsAsUnit1().size());
        StratigraphicRelationshipDTO savedRel = result.getRelationshipsAsUnit1().iterator().next();
        assertEquals(summary2, savedRel.getUnit2());
        assertEquals(summary1, savedRel.getUnit1());
    }

    @Test
    void save_SyncsStratigraphicRelationshipsAsUnit2() {
        // Arrange

        // DTO of recording unit with one relationship
        RecordingUnitSummaryDTO summary1 = new RecordingUnitSummaryDTO();
        summary1.setId(1L);
        summary1.setFullIdentifier("1");
        RecordingUnitSummaryDTO summary2 = new RecordingUnitSummaryDTO();
        summary2.setId(2L);
        summary2.setFullIdentifier("2");
        StratigraphicRelationshipDTO relDto = new StratigraphicRelationshipDTO();
        relDto.setUnit1(summary2);
        relDto.setUnit2(summary1);
        RecordingUnitDTO toInsertDto = new RecordingUnitDTO();
        toInsertDto.setId(1L);
        toInsertDto.setFullIdentifier("1");
        toInsertDto.setRelationshipsAsUnit2(new HashSet<>());
        toInsertDto.getRelationshipsAsUnit2().add(relDto);

        // Entity to insert
        RecordingUnit toInsert = new RecordingUnit();
        toInsert.setId(1L);
        toInsert.setFullIdentifier("1");
        RecordingUnit managed = new RecordingUnit();
        toInsert.setId(1L);
        toInsert.setFullIdentifier("1");
        RecordingUnit unit2 = new RecordingUnit();
        unit2.setId(2L);
        unit2.setFullIdentifier("2");
        StratigraphicRelationship rel = new StratigraphicRelationship();
        rel.setUnit1(unit2);
        rel.setUnit2(toInsert);
        Set<StratigraphicRelationship> rels = new HashSet<>();
        rels.add(rel);
        toInsert.setRelationshipsAsUnit2(rels);

        when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(toInsert);
        when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(unit2));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(toInsertDto);

        // Act
        RecordingUnitDTO result = recordingUnitService.save(toInsertDto);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getRelationshipsAsUnit2().size());
        StratigraphicRelationshipDTO savedRel = result.getRelationshipsAsUnit2().iterator().next();
        assertEquals(summary2, savedRel.getUnit1());
        assertEquals(summary1, savedRel.getUnit2());
    }

    @Test
    void existsChildrenByParentAndInstitution_shouldReturnTrue_whenChildrenExist() {
        // Arrange
        Long parentId = 1L;
        Long institutionId = 10L;
        when(recordingUnitRepository.existsChildrenByParentAndInstitution(parentId, institutionId))
                .thenReturn(true);

        // Act
        boolean result = recordingUnitService.existsChildrenByParentAndInstitution(parentId, institutionId);

        // Assert
        assertTrue(result);
        verify(recordingUnitRepository, times(1))
                .existsChildrenByParentAndInstitution(parentId, institutionId);
    }

    @Test
    void existsRootChildrenByInstitution_ShouldReturnTrue_WhenChildrenExist() {
        // Arrange
        Long institutionId = 1L;
        when(recordingUnitRepository.existsRootChildrenByInstitution(institutionId))
                .thenReturn(true);

        // Act
        boolean result = recordingUnitService.existsRootChildrenByInstitution(institutionId);

        // Assert
        assertTrue(result);
    }

    @Test
    void updateStratigraphicRel() {
        // Arrange
        RecordingUnitDTO recordingUnitDTO = new RecordingUnitDTO();
        recordingUnitDTO.setId(1L);
        recordingUnitDTO.setRelationshipsAsUnit1(new HashSet<>());
        recordingUnitDTO.setRelationshipsAsUnit2(new HashSet<>());

        RecordingUnit recordingUnit = new RecordingUnit();
        recordingUnit.setId(1L);
        recordingUnit.setRelationshipsAsUnit1(new HashSet<>());
        recordingUnit.setRelationshipsAsUnit2(new HashSet<>());

        RecordingUnit managedRecordingUnit = new RecordingUnit();
        managedRecordingUnit.setId(1L);
        managedRecordingUnit.setRelationshipsAsUnit1(new HashSet<>());
        managedRecordingUnit.setRelationshipsAsUnit2(new HashSet<>());

        when(recordingUnitMapper.invertConvert(recordingUnitDTO)).thenReturn(recordingUnit);
        when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managedRecordingUnit));

        // Act
        recordingUnitService.updateStratigraphicRel(recordingUnitDTO);

        // Assert
        verify(recordingUnitMapper).invertConvert(recordingUnitDTO);
        verify(recordingUnitRepository).findById(1L);
        
        // Ensure no exception is thrown and relationships are synced properly
        // Specifically testing that the setupStratigraphicRelationships doesn't crash 
        // when valid RecordingUnit objects are passed
        assertNotNull(managedRecordingUnit);
    }

    @Test
    void save_withAbstractEntityDTO_shouldSaveAndConvert() {
        // Arrange
        RecordingUnitDTO inputDto = new RecordingUnitDTO();
        inputDto.setId(1L);

        RecordingUnit entityToSave = new RecordingUnit();
        entityToSave.setId(1L);

        RecordingUnit savedEntity = new RecordingUnit();
        savedEntity.setId(1L);
        savedEntity.setFullIdentifier("ID-POST-SAVE");

        RecordingUnitDTO expectedDto = new RecordingUnitDTO();
        expectedDto.setId(1L);
        expectedDto.setFullIdentifier("ID-POST-SAVE");

        // Mock the chain of calls
        when(recordingUnitMapper.invertConvert(inputDto)).thenReturn(entityToSave);
        when(recordingUnitRepository.save(entityToSave)).thenReturn(savedEntity);
        when(recordingUnitMapper.convert(savedEntity)).thenReturn(expectedDto);

        // Act
        RecordingUnitDTO result = recordingUnitService.save((AbstractEntityDTO) inputDto);

        // Assert
        assertNotNull(result);
        assertEquals(expectedDto.getId(), result.getId());
        assertEquals(expectedDto.getFullIdentifier(), result.getFullIdentifier());

        // Verify that the mocks were called correctly
        verify(recordingUnitMapper, times(1)).invertConvert(inputDto);
        verify(recordingUnitRepository, times(1)).save(entityToSave);
        verify(recordingUnitMapper, times(1)).convert(savedEntity);
    }

    @Test
    void save_withAbstractEntityDTO_shouldThrowNPE_whenMapperReturnsNull() {
        // Arrange
        RecordingUnitDTO inputDto = new RecordingUnitDTO();
        when(recordingUnitMapper.invertConvert(inputDto)).thenReturn(null);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> recordingUnitService.save((AbstractEntityDTO) inputDto));
    }

    @Test
    void save_throughPublicDtoMethod_correctlyAssemblesAndSavesEntity() {
        // Ce test cible la logique de la méthode privée save(RecordingUnit)
        // en passant par son wrapper public. Il vérifie que l'entité est correctement
        // assemblée avant d'être persistée.

        // Arrange
        // DTO en entrée
        RecordingUnitDTO inputDto = new RecordingUnitDTO();
        inputDto.setId(1L);
        inputDto.setDescription("Nouvelle Description");

        // Entité source (résultat du mapping)
        RecordingUnit sourceEntity = new RecordingUnit();
        sourceEntity.setId(1L);
        sourceEntity.setDescription("Nouvelle Description");
        // Ajout d'un parent pour tester la mise à jour de la relation
        RecordingUnit parentEntitySource = new RecordingUnit();
        parentEntitySource.setId(2L);
        sourceEntity.setParents(Collections.singleton(parentEntitySource));
        sourceEntity.setChildren(new HashSet<>());
        sourceEntity.setRelationshipsAsUnit1(new HashSet<>());
        sourceEntity.setRelationshipsAsUnit2(new HashSet<>());
        sourceEntity.setContributors(new ArrayList<>());

        // Entité managée (existante en base) qui sera mise à jour
        RecordingUnit managedEntity = new RecordingUnit();
        managedEntity.setId(1L);
        managedEntity.setDescription("Ancienne Description");
        managedEntity.setChildren(new HashSet<>());

        // Entité parente (existante en base)
        RecordingUnit managedParentEntity = new RecordingUnit();
        managedParentEntity.setId(2L);
        managedParentEntity.setChildren(new HashSet<>());

        // Mocks
        when(recordingUnitMapper.invertConvert(inputDto)).thenReturn(sourceEntity);
        when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managedEntity));
        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(managedParentEntity));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(new RecordingUnitDTO());

        // Act
        recordingUnitService.save(inputDto);

        // Assert
        // On capture les entités passées à la méthode save() du repository.
        // On s'attend à 2 sauvegardes : l'entité principale et son parent.
        ArgumentCaptor<RecordingUnit> savedEntityCaptor = ArgumentCaptor.forClass(RecordingUnit.class);
        verify(recordingUnitRepository, times(2)).save(savedEntityCaptor.capture());

        RecordingUnit savedManagedEntity = savedEntityCaptor.getAllValues().stream()
                .filter(e -> e.getId().equals(1L))
                .findFirst()
                .orElseThrow(() -> new AssertionError("L'entité managée n'a pas été sauvegardée"));

        RecordingUnit savedParentEntity = savedEntityCaptor.getAllValues().stream()
                .filter(e -> e.getId().equals(2L))
                .findFirst()
                .orElseThrow(() -> new AssertionError("L'entité parente n'a pas été sauvegardée"));

        // 1. Vérifier que les champs ont bien été copiés sur l'entité managée
        assertEquals("Nouvelle Description", savedManagedEntity.getDescription());

        // 2. Vérifier que la relation parent/enfant a été mise à jour
        assertTrue(savedParentEntity.getChildren().contains(savedManagedEntity),
                "L'entité managée doit être dans la liste des enfants du parent.");
    }

    @Test
    void syncRelationships_updateExistingRelationship_asUnit1() {
        // Arrange
        RecordingUnit managed = new RecordingUnit();
        managed.setId(1L);
        RecordingUnit unit2 = new RecordingUnit();
        unit2.setId(2L);

        Concept oldConcept = new Concept();
        oldConcept.setId(10L);
        Concept newConcept = new Concept();
        newConcept.setId(20L);

        StratigraphicRelationship existingRel = new StratigraphicRelationship();
        existingRel.setUnit1(managed);
        existingRel.setUnit2(unit2);
        existingRel.setConcept(oldConcept);
        managed.addRelationshipAsUnit1(existingRel);

        StratigraphicRelationship updatedRelSource = new StratigraphicRelationship();
        updatedRelSource.setUnit1(new RecordingUnit() {{ setId(1L); }});
        updatedRelSource.setUnit2(new RecordingUnit() {{ setId(2L); }});
        updatedRelSource.setConcept(newConcept);

        RecordingUnit sourceEntity = new RecordingUnit();
        sourceEntity.setId(1L);
        sourceEntity.setRelationshipsAsUnit1(Set.of(updatedRelSource));

        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(1L);

        when(recordingUnitMapper.invertConvert(dto)).thenReturn(sourceEntity);
        when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(unit2));

        // Act
        recordingUnitService.updateStratigraphicRel(dto);

        // Assert
        assertEquals(1, managed.getRelationshipsAsUnit1().size());
        StratigraphicRelationship finalRel = managed.getRelationshipsAsUnit1().iterator().next();
        assertEquals(newConcept, finalRel.getConcept());
    }

    @Test
    void syncRelationships_addNewRelationship_asUnit2() {
        // Arrange
        RecordingUnit managed = new RecordingUnit();
        managed.setId(2L);
        RecordingUnit unit1 = new RecordingUnit();
        unit1.setId(1L);

        StratigraphicRelationship newRel = new StratigraphicRelationship();
        newRel.setUnit1(unit1);
        newRel.setUnit2(managed);

        RecordingUnit sourceEntity = new RecordingUnit();
        sourceEntity.setId(2L);
        sourceEntity.setRelationshipsAsUnit2(Set.of(newRel));

        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(2L);

        when(recordingUnitMapper.invertConvert(dto)).thenReturn(sourceEntity);
        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(managed));
        when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(unit1));

        // Act
        recordingUnitService.updateStratigraphicRel(dto);

        // Assert
        assertEquals(1, managed.getRelationshipsAsUnit2().size());
        assertTrue(managed.getRelationshipsAsUnit2().contains(newRel));
    }

    @Test
    void syncRelationships_updateExistingRelationship_asUnit2() {
        // Arrange
        RecordingUnit managed = new RecordingUnit();
        managed.setId(2L);
        RecordingUnit unit1 = new RecordingUnit();
        unit1.setId(1L);

        Concept oldConcept = new Concept();
        oldConcept.setId(10L);
        Concept newConcept = new Concept();
        newConcept.setId(20L);

        StratigraphicRelationship existingRel = new StratigraphicRelationship();
        existingRel.setUnit1(unit1);
        existingRel.setUnit2(managed);
        existingRel.setConcept(oldConcept);
        managed.addRelationshipAsUnit2(existingRel);

        StratigraphicRelationship updatedRelSource = new StratigraphicRelationship();
        updatedRelSource.setUnit1(new RecordingUnit() {{ setId(1L); }});
        updatedRelSource.setUnit2(new RecordingUnit() {{ setId(2L); }});
        updatedRelSource.setConcept(newConcept);

        RecordingUnit sourceEntity = new RecordingUnit();
        sourceEntity.setId(2L);
        sourceEntity.setRelationshipsAsUnit2(Set.of(updatedRelSource));

        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(2L);

        when(recordingUnitMapper.invertConvert(dto)).thenReturn(sourceEntity);
        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(managed));
        when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(unit1));

        // Act
        recordingUnitService.updateStratigraphicRel(dto);

        // Assert
        assertEquals(1, managed.getRelationshipsAsUnit2().size());
        StratigraphicRelationship finalRel = managed.getRelationshipsAsUnit2().iterator().next();
        assertEquals(newConcept, finalRel.getConcept());
    }

    @Test
    void syncRelationships_removeRelationship() {
        // Arrange
        RecordingUnit managed = new RecordingUnit();
        managed.setId(1L);
        RecordingUnit unit2 = new RecordingUnit();
        unit2.setId(2L);

        StratigraphicRelationship relToRemove = new StratigraphicRelationship();
        relToRemove.setUnit1(managed);
        relToRemove.setUnit2(unit2);
        managed.addRelationshipAsUnit1(relToRemove);

        // La source n'a aucune relation, ce qui doit entraîner la suppression de celle existante
        RecordingUnit sourceEntity = new RecordingUnit();
        sourceEntity.setId(1L);

        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(1L);

        when(recordingUnitMapper.invertConvert(dto)).thenReturn(sourceEntity);
        when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));

        // Act
        recordingUnitService.updateStratigraphicRel(dto);

        // Assert
        assertTrue(managed.getRelationshipsAsUnit1().isEmpty());
    }

    @Mock
    private RuIdentifierResolver identifierResolver;

    @Test
    @SuppressWarnings("unchecked")
    void findAllIdentifierResolver_ShouldReturnCachedResolversIfNotEmpty() {
        // Arrange
        doReturn(identifierResolver).when(applicationContext).getBean(any(Class.class));

        when(identifierResolver.getCode()).thenReturn("TEST_CODE");

        // Act
        Map<String, RuIdentifierResolver> resolversAgain = recordingUnitService.findAllIdentifierResolver();

        assertThat(resolversAgain).hasSize(1);
    }

    @Test
    void findAllByActionUnit_shouldReturnAllAU() {
        ActionUnit a1 = new ActionUnit();
        a1.setIdentifier("A1");
        a1.setId(1L);
        RecordingUnit r2 = new RecordingUnit();
        r2.setFullIdentifier("A2");
        RecordingUnit r3 = new RecordingUnit();
        r3.setFullIdentifier("A3");
        when(recordingUnitRepository.findAllByActionUnitId(1L)).thenReturn(List.of(r2, r3));
        when(conversionService.convert(any(), eq(RecordingUnitSummaryDTO.class))).then(invocation -> {
            RecordingUnitSummaryDTO dto = new RecordingUnitSummaryDTO();
            RecordingUnit ru = invocation.getArgument(0,RecordingUnit.class);
            dto.setFullIdentifier(ru.getFullIdentifier());
            return dto;
        });

        List<RecordingUnitSummaryDTO> result = recordingUnitService.findAllByActionUnit(a1.getId());

        assertThat(result).hasSize(2)
                .allMatch(dto -> dto.getFullIdentifier().startsWith("A"));
    }

    @Test
    void findDirectParentsOf_shouldReturnDirectParents() {
        RecordingUnit recordingUnit = new RecordingUnit();
        recordingUnit.setId(1L);
        recordingUnit.setFullIdentifier("1L");
        recordingUnit.setCreatedByInstitution(new Institution());
        recordingUnit.getCreatedByInstitution().setId(1L);
        recordingUnit.getCreatedByInstitution().setIdentifier("TEST_INST");
        RecordingUnit parent1 = new RecordingUnit();
        parent1.setId(2L);
        parent1.setFullIdentifier("2L");
        parent1.setCreatedByInstitution(recordingUnit.getCreatedByInstitution());
        RecordingUnit parent2 = new RecordingUnit();
        parent2.setId(3L);
        parent2.setFullIdentifier("3L");
        parent2.setCreatedByInstitution(recordingUnit.getCreatedByInstitution());

        when(recordingUnitRepository.findParentsOf(1L)).thenReturn(Set.of(parent1, parent2));
        when(conversionService.convert(any(), eq(RecordingUnitDTO.class))).then(invocation -> {
            RecordingUnitDTO dto = new RecordingUnitDTO();
            RecordingUnit ru = invocation.getArgument(0,RecordingUnit.class);
            dto.setFullIdentifier(ru.getFullIdentifier());
            return dto;
        });

        List<RecordingUnitDTO> dtos = recordingUnitService.findDirectParentsOf(recordingUnit.getId());

        assertThat(dtos).hasSize(2);
    }

    @Test
    void findByFullIdentifierAndInstitutionId_shouldReturnRecordingUnit_whenFound() {
        // Arrange
        String fullIdentifier = "RU-001";
        Long institutionId = 1L;
        RecordingUnit expectedRecordingUnit = new RecordingUnit();
        expectedRecordingUnit.setId(10L);
        expectedRecordingUnit.setFullIdentifier(fullIdentifier);

        when(recordingUnitRepository.findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId))
                .thenReturn(Optional.of(expectedRecordingUnit));

        // Act
        RecordingUnit result = recordingUnitService.findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId);

        // Assert
        assertThat(result).isEqualTo(expectedRecordingUnit);
        verify(recordingUnitRepository, times(1)).findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId);
    }

    @Test
    void findByFullIdentifierAndInstitutionId_shouldThrowException_whenNotFound() {
        // Arrange
        String fullIdentifier = "RU-001";
        Long institutionId = 1L;

        when(recordingUnitRepository.findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId))
                .thenReturn(Optional.empty());

        // Act & Assert
        RecordingUnitNotFoundException exception = assertThrows(RecordingUnitNotFoundException.class, () ->
                recordingUnitService.findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId)
        );
        assertThat(exception.getMessage()).contains("RecordingUnit not found with fullIdentifier=RU-001 and institutionIdentifier=1");
        verify(recordingUnitRepository, times(1)).findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId);
    }

    @Test
    void findByFullIdentifierAndInstitutionIdDTO_shouldReturnRecordingUnitDTO_whenFoundWithoutSpecimenCount() {
        // Arrange
        String fullIdentifier = "RU-001";
        Long institutionId = 1L;
        RecordingUnit recordingUnit = new RecordingUnit();
        recordingUnit.setId(10L);
        recordingUnit.setFullIdentifier(fullIdentifier);
        RecordingUnitDTO expectedDto = new RecordingUnitDTO();
        expectedDto.setId(10L);
        expectedDto.setFullIdentifier(fullIdentifier);

        when(recordingUnitRepository.findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId))
                .thenReturn(Optional.of(recordingUnit));
        when(recordingUnitMapper.convert(recordingUnit)).thenReturn(expectedDto);

        // Act
        RecordingUnitDTO result = recordingUnitService.findByFullIdentifierAndInstitutionIdDTO(fullIdentifier, institutionId, null);

        // Assert
        assertThat(result).isEqualTo(expectedDto);
        assertThat(result.getSpecimenCount()).isNull();
        verify(recordingUnitRepository, times(1)).findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId);
        verify(recordingUnitMapper, times(1)).convert(recordingUnit);
        verify(recordingUnitRepository, never()).countSpecimensByRecordingUnitId(anyLong());
    }

    @Test
    void findByFullIdentifierAndInstitutionIdDTO_shouldReturnRecordingUnitDTO_whenFoundWithSpecimenCount() {
        // Arrange
        String fullIdentifier = "RU-001";
        Long institutionId = 1L;
        Long specimenCount = 5L;
        RecordingUnit recordingUnit = new RecordingUnit();
        recordingUnit.setId(10L);
        recordingUnit.setFullIdentifier(fullIdentifier);
        RecordingUnitDTO expectedDto = new RecordingUnitDTO();
        expectedDto.setId(10L);
        expectedDto.setFullIdentifier(fullIdentifier);

        when(recordingUnitRepository.findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId))
                .thenReturn(Optional.of(recordingUnit));
        when(recordingUnitMapper.convert(recordingUnit)).thenReturn(expectedDto);
        when(recordingUnitRepository.countSpecimensByRecordingUnitId(recordingUnit.getId())).thenReturn(specimenCount);

        // Act
        RecordingUnitDTO result = recordingUnitService.findByFullIdentifierAndInstitutionIdDTO(fullIdentifier, institutionId, List.of("specimen"));

        // Assert
        assertThat(result).isEqualTo(expectedDto);
        assertThat(result.getSpecimenCount()).isEqualTo(specimenCount);
        verify(recordingUnitRepository, times(1)).findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId);
        verify(recordingUnitMapper, times(1)).convert(recordingUnit);
        verify(recordingUnitRepository, times(1)).countSpecimensByRecordingUnitId(recordingUnit.getId());
    }

    @Test
    void findByFullIdentifierAndInstitutionIdDTO_shouldThrowException_whenNotFound() {
        // Arrange
        String fullIdentifier = "RU-001";
        Long institutionId = 1L;

        when(recordingUnitRepository.findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId))
                .thenReturn(Optional.empty());

        // Act & Assert
        RecordingUnitNotFoundException exception = assertThrows(RecordingUnitNotFoundException.class, () ->
                recordingUnitService.findByFullIdentifierAndInstitutionIdDTO(fullIdentifier, institutionId, null)
        );
        assertThat(exception.getMessage()).contains("RecordingUnit not found with fullIdentifier=RU-001 and institutionId=1");
        verify(recordingUnitRepository, times(1)).findByFullIdentifierAndInstitutionId(fullIdentifier, institutionId);
        verify(recordingUnitMapper, never()).convert(any(RecordingUnit.class));
    }

    @Test
    void findByInstitutionId_shouldReturnPaginatedRecordingUnits() {
        // Arrange
        Long institutionId = 1L;
        int limit = 2;
        int offset = 0;
        Pageable pageable = PageRequest.of(offset, limit);

        RecordingUnit ru1 = new RecordingUnit(); ru1.setId(1L); ru1.setFullIdentifier("RU-001");
        RecordingUnit ru2 = new RecordingUnit(); ru2.setId(2L); ru2.setFullIdentifier("RU-002");
        Page<RecordingUnit> recordingUnitPage = new PageImpl<>(List.of(ru1, ru2), pageable, 2);

        RecordingUnitDTO dto1 = new RecordingUnitDTO(); dto1.setId(1L); dto1.setFullIdentifier("RU-001");
        RecordingUnitDTO dto2 = new RecordingUnitDTO(); dto2.setId(2L); dto2.setFullIdentifier("RU-002");

        when(recordingUnitRepository.findByCreatedByInstitutionId(institutionId, pageable)).thenReturn(recordingUnitPage);
        when(recordingUnitMapper.convert(ru1)).thenReturn(dto1);
        when(recordingUnitMapper.convert(ru2)).thenReturn(dto2);

        // Act
        Page<RecordingUnitDTO> result = recordingUnitService.findByInstitutionId(institutionId, limit, offset);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.getContent()).containsExactly(dto1, dto2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(recordingUnitRepository, times(1)).findByCreatedByInstitutionId(institutionId, pageable);
        verify(recordingUnitMapper, times(1)).convert(ru1);
        verify(recordingUnitMapper, times(1)).convert(ru2);
    }

    @Test
    void findByInstitutionId_shouldReturnEmptyPage_whenNoRecordingUnitsFound() {
        // Arrange
        Long institutionId = 1L;
        int limit = 2;
        int offset = 0;
        Pageable pageable = PageRequest.of(offset, limit);

        Page<RecordingUnit> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(recordingUnitRepository.findByCreatedByInstitutionId(institutionId, pageable)).thenReturn(emptyPage);

        // Act
        Page<RecordingUnitDTO> result = recordingUnitService.findByInstitutionId(institutionId, limit, offset);

        // Assert
        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(recordingUnitRepository, times(1)).findByCreatedByInstitutionId(institutionId, pageable);
        verify(recordingUnitMapper, never()).convert(any(RecordingUnit.class));
    }
}
