package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuIdentifierResolver;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.infrastructure.database.repositories.ArkRepository;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.form.CustomFormResponseRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdCounterRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdInfoRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.StratigraphicRelationshipRepository;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.mapper.PhaseMapper;
import fr.siamois.mapper.RecordingUnitMapper;
import fr.siamois.mapper.RecordingUnitSummaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import org.springframework.data.jpa.domain.Specification;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;


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

    @Mock
    private StratigraphicRelationshipRepository stratigraphicRelationshipRepository;

    @Mock
    private RecordingUnitSummaryMapper recordingUnitSummaryMapper;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private CustomFormResponseRepository customFormResponseRepository;

    @Mock
    private ArkRepository arkRepository;

    @Mock
    private PhaseRepository phaseRepository;

    @Mock
    private PhaseMapper phaseMapper;

    @Mock
    private RecordingUnitIdCounterRepository recordingUnitIdCounterRepository;

    @Mock
    private RecordingUnitIdInfoRepository recordingUnitIdInfoRepository;

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
        when(recordingUnitRepository.findAllById(argThat(ids -> {
            List<Long> collected = new ArrayList<>();
            ids.forEach(collected::add);
            return collected.size() == 1 && collected.get(0).equals(2L);
        }))).thenReturn(List.of(managedParentEntity));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(recordingUnitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(new RecordingUnitDTO());

        // Act
        recordingUnitService.save(inputDto);

        // Assert
        verify(recordingUnitRepository, times(1)).save(any(RecordingUnit.class));
        verify(recordingUnitRepository, times(1)).saveAll(anyList());

        assertEquals("Nouvelle Description", managedEntity.getDescription());
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


    // =====================================================================
    // autocompleteInActionUnit / searchRecordingUnitInRecordingUnit /
    // countSearchResultsInRecordingUnit
    // =====================================================================

    @Nested
    class AutocompleteAndSearchInRecordingUnitTests {

        InstitutionDTO institution;
        RecordingUnitDTO recordingUnitDTO;
        Pageable pageable;
        RecordingUnit ru;
        RecordingUnitDTO ruDTO;

        @BeforeEach
        void init() {
            institution = new InstitutionDTO();
            institution.setId(1L);

            recordingUnitDTO = new RecordingUnitDTO();
            recordingUnitDTO.setId(7L);

            pageable = PageRequest.of(0, 10);

            ru = new RecordingUnit();
            ru.setId(42L);

            ruDTO = new RecordingUnitDTO();
            ruDTO.setId(42L);
        }

        // ------------------------------------------------------------------
        // autocompleteInActionUnit
        // ------------------------------------------------------------------

        @Test
        void autocompleteInActionUnit_withQuery_delegatesToRepositoryAndConverts() {
            RecordingUnitSummaryDTO summaryDTO = new RecordingUnitSummaryDTO();
            when(recordingUnitRepository
                    .findByActionUnitIdAndFullIdentifierContainingIgnoreCaseOrderByFullIdentifierAsc(
                            eq(10L), eq("US-"), any(Pageable.class)))
                    .thenReturn(List.of(ru));
            when(conversionService.convert(ru, RecordingUnitSummaryDTO.class)).thenReturn(summaryDTO);

            List<RecordingUnitSummaryDTO> result =
                    recordingUnitService.autocompleteInActionUnit(10L, "US-", 5);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isSameAs(summaryDTO);
            verify(recordingUnitRepository)
                    .findByActionUnitIdAndFullIdentifierContainingIgnoreCaseOrderByFullIdentifierAsc(
                            eq(10L), eq("US-"), any(Pageable.class));
        }

        @Test
        void autocompleteInActionUnit_nullQuery_treatedAsEmptyString() {
            when(recordingUnitRepository
                    .findByActionUnitIdAndFullIdentifierContainingIgnoreCaseOrderByFullIdentifierAsc(
                            eq(10L), eq(""), any(Pageable.class)))
                    .thenReturn(List.of());

            List<RecordingUnitSummaryDTO> result =
                    recordingUnitService.autocompleteInActionUnit(10L, null, 5);

            assertThat(result).isEmpty();
            verify(recordingUnitRepository)
                    .findByActionUnitIdAndFullIdentifierContainingIgnoreCaseOrderByFullIdentifierAsc(
                            eq(10L), eq(""), any(Pageable.class));
        }

        @Test
        void autocompleteInActionUnit_respectsLimit() {
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            when(recordingUnitRepository
                    .findByActionUnitIdAndFullIdentifierContainingIgnoreCaseOrderByFullIdentifierAsc(
                            anyLong(), anyString(), pageableCaptor.capture()))
                    .thenReturn(List.of());

            recordingUnitService.autocompleteInActionUnit(10L, "x", 3);

            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
        }

        @Test
        void autocompleteInActionUnit_noResults_returnsEmptyList() {
            when(recordingUnitRepository
                    .findByActionUnitIdAndFullIdentifierContainingIgnoreCaseOrderByFullIdentifierAsc(
                            anyLong(), anyString(), any()))
                    .thenReturn(List.of());

            assertThat(recordingUnitService.autocompleteInActionUnit(10L, "zzz", 10)).isEmpty();
            verifyNoInteractions(conversionService);
        }

        // ------------------------------------------------------------------
        // searchRecordingUnitInRecordingUnit
        // ------------------------------------------------------------------

        @Test
        void searchRecordingUnitInRecordingUnit_happyPath_delegatesAndMapsPage() {
            FilterDTO filters = new FilterDTO(false);
            Page<RecordingUnit> page = new PageImpl<>(List.of(ru));

            when(recordingUnitRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(page);
            when(recordingUnitMapper.convert(ru)).thenReturn(ruDTO);

            Page<RecordingUnitDTO> result = recordingUnitService.searchRecordingUnitInRecordingUnit(
                    institution, recordingUnitDTO, filters, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0)).isSameAs(ruDTO);
            verify(recordingUnitRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        void searchRecordingUnitInRecordingUnit_emptyPage_returnsEmpty() {
            FilterDTO filters = new FilterDTO(false);
            when(recordingUnitRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<RecordingUnitDTO> result = recordingUnitService.searchRecordingUnitInRecordingUnit(
                    institution, recordingUnitDTO, filters, pageable);

            assertTrue(result.isEmpty());
            verifyNoInteractions(recordingUnitMapper);
        }

        @Test
        void searchRecordingUnitInRecordingUnit_rootOnlyFalse_neverCallsListFindAll() {
            FilterDTO filters = new FilterDTO(false);
            filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(5L), FilterDTO.FilterType.EQUAL);
            when(recordingUnitRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of()));

            recordingUnitService.searchRecordingUnitInRecordingUnit(
                    institution, recordingUnitDTO, filters, pageable);

            verify(recordingUnitRepository, never()).findAll(any(Specification.class));
        }

        // ------------------------------------------------------------------
        // countSearchResultsInRecordingUnit
        // ------------------------------------------------------------------

        @Test
        void countSearchResultsInRecordingUnit_noFilters_returnsRepositoryCount() {
            FilterDTO filters = new FilterDTO(false);
            when(recordingUnitRepository.count(any(Specification.class))).thenReturn(8L);

            int result = recordingUnitService.countSearchResultsInRecordingUnit(
                    institution, recordingUnitDTO, filters);

            assertThat(result).isEqualTo(8);
            verify(recordingUnitRepository).count(any(Specification.class));
        }

        @Test
        void countSearchResultsInRecordingUnit_withUserFilters_returnsRepositoryCount() {
            FilterDTO filters = new FilterDTO(false);
            filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(3L), FilterDTO.FilterType.EQUAL);
            when(recordingUnitRepository.count(any(Specification.class))).thenReturn(3L);

            int result = recordingUnitService.countSearchResultsInRecordingUnit(
                    institution, recordingUnitDTO, filters);

            assertThat(result).isEqualTo(3);
        }

        @Test
        void countSearchResultsInRecordingUnit_rootOnlyTrue_noUserFilters_countWithRootSpec() {
            FilterDTO filters = new FilterDTO(true);
            when(recordingUnitRepository.count(any(Specification.class))).thenReturn(2L);

            int result = recordingUnitService.countSearchResultsInRecordingUnit(
                    institution, recordingUnitDTO, filters);

            assertThat(result).isEqualTo(2);
            verify(recordingUnitRepository).count(any(Specification.class));
            verify(recordingUnitRepository, never()).findAll(any(Specification.class));
        }

        @Test
        void countSearchResultsInRecordingUnit_rootOnlyTrue_userFilters_resolvesClosureFirst() {
            FilterDTO filters = new FilterDTO(true);
            filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(9L), FilterDTO.FilterType.EQUAL);

            ru.setId(42L);
            when(recordingUnitRepository.findAll(any(Specification.class)))
                    .thenReturn(List.of(ru));
            when(recordingUnitRepository.findAncestorClosure(new Long[]{42L}))
                    .thenReturn(List.of(42L, 1L));
            when(recordingUnitRepository.count(any(Specification.class))).thenReturn(1L);

            int result = recordingUnitService.countSearchResultsInRecordingUnit(
                    institution, recordingUnitDTO, filters);

            assertThat(result).isEqualTo(1);
            verify(recordingUnitRepository).findAll(any(Specification.class));
            verify(recordingUnitRepository).findAncestorClosure(new Long[]{42L});
        }

        @Test
        void countSearchResultsInRecordingUnit_rootOnlyTrue_userFilters_noMatches_returnsZero() {
            FilterDTO filters = new FilterDTO(true);
            filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(9L), FilterDTO.FilterType.EQUAL);

            when(recordingUnitRepository.findAll(any(Specification.class)))
                    .thenReturn(List.of());
            when(recordingUnitRepository.count(any(Specification.class))).thenReturn(0L);

            int result = recordingUnitService.countSearchResultsInRecordingUnit(
                    institution, recordingUnitDTO, filters);

            assertThat(result).isZero();
            verify(recordingUnitRepository, never()).findAncestorClosure(any());
        }

        @Test
        void countSearchResultsInRecordingUnit_cachedClosure_skipsFindAllListVariant() {
            FilterDTO filters = new FilterDTO(true);
            filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(9L), FilterDTO.FilterType.EQUAL);
            filters.setAncestorClosure(Set.of(42L, 1L));

            when(recordingUnitRepository.count(any(Specification.class))).thenReturn(2L);

            recordingUnitService.countSearchResultsInRecordingUnit(
                    institution, recordingUnitDTO, filters);

            verify(recordingUnitRepository, never()).findAll(any(Specification.class));
            verify(recordingUnitRepository, never()).findAncestorClosure(any());
        }
    }

    // =====================================================================
    // synchronizeCollection (phases) via save(RecordingUnit)
    // =====================================================================

    @Nested
    class SynchronizeCollectionTests {

        @Test
        void save_synchronizesPhases_addsNewAndRemovesStale() {
            Phase p1 = new Phase();
            p1.setId(1L);
            Phase p2 = new Phase();
            p2.setId(2L);
            Phase p3 = new Phase();
            p3.setId(3L);

            RecordingUnit source = new RecordingUnit();
            source.setId(1L);
            source.setFullIdentifier("SRC-1");
            source.setPhases(new HashSet<>(Set.of(p2, p3)));
            source.setParents(null);
            source.setChildren(null);

            RecordingUnit managed = new RecordingUnit();
            managed.setId(1L);
            managed.setFullIdentifier("MANAGED-1");
            managed.setPhases(new HashSet<>(Set.of(p1, p2)));

            when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(source);
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
            when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(new RecordingUnitDTO());

            recordingUnitService.save(new RecordingUnitDTO());

            assertEquals(Set.of(p2, p3), managed.getPhases());
        }

        @Test
        void save_synchronizesPhases_nullIncomingClearsManaged() {
            Phase p1 = new Phase();
            p1.setId(1L);

            RecordingUnit source = new RecordingUnit();
            source.setId(1L);
            source.setFullIdentifier("SRC-1");
            source.setPhases(null);
            source.setParents(null);
            source.setChildren(null);

            RecordingUnit managed = new RecordingUnit();
            managed.setId(1L);
            managed.setFullIdentifier("MANAGED-1");
            managed.setPhases(new HashSet<>(Set.of(p1)));

            when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(source);
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
            when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(new RecordingUnitDTO());

            recordingUnitService.save(new RecordingUnitDTO());

            assertTrue(managed.getPhases().isEmpty());
        }
    }

    // =====================================================================
    // setupChilds / setupParents via save(RecordingUnit)
    // =====================================================================

    @Nested
    class SetupChildsAndParentsTests {

        @Test
        void save_setupChilds_addsNewChildAndRemovesStaleChild() {
            RecordingUnit child5 = new RecordingUnit();
            child5.setId(5L);
            child5.setFullIdentifier("CHILD-5");
            RecordingUnit child6 = new RecordingUnit();
            child6.setId(6L);
            child6.setFullIdentifier("CHILD-6");

            RecordingUnit incomingChild5 = new RecordingUnit();
            incomingChild5.setId(5L);

            RecordingUnit source = new RecordingUnit();
            source.setId(1L);
            source.setFullIdentifier("SRC-1");
            source.setChildren(new HashSet<>(Set.of(incomingChild5)));
            source.setParents(null);

            RecordingUnit managed = new RecordingUnit();
            managed.setId(1L);
            managed.setFullIdentifier("MANAGED-1");
            managed.setChildren(new HashSet<>(Set.of(child6)));

            when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(source);
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
            when(recordingUnitRepository.findAllById(argThat(ids -> {
                List<Long> collected = new ArrayList<>();
                ids.forEach(collected::add);
                return collected.equals(List.of(5L));
            }))).thenReturn(List.of(child5));
            when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(new RecordingUnitDTO());

            recordingUnitService.save(new RecordingUnitDTO());

            assertEquals(Set.of(child5), managed.getChildren());
        }

        @Test
        void save_setupChilds_nullChildren_isNoop() {
            RecordingUnit source = new RecordingUnit();
            source.setId(1L);
            source.setFullIdentifier("SRC-1");
            source.setChildren(null);
            source.setParents(null);

            RecordingUnit managed = new RecordingUnit();
            managed.setId(1L);
            managed.setFullIdentifier("MANAGED-1");
            RecordingUnit existingChild = new RecordingUnit();
            existingChild.setId(6L);
            existingChild.setFullIdentifier("CHILD-6");
            managed.setChildren(new HashSet<>(Set.of(existingChild)));

            when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(source);
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
            when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(new RecordingUnitDTO());

            recordingUnitService.save(new RecordingUnitDTO());

            assertEquals(Set.of(existingChild), managed.getChildren());
        }

        @Test
        void save_setupChilds_missingChildThrows_wrappedAsFailedRecordingUnitSaveException() {
            RecordingUnit missingChild = new RecordingUnit();
            missingChild.setId(99L);

            RecordingUnit source = new RecordingUnit();
            source.setId(1L);
            source.setFullIdentifier("SRC-1");
            source.setChildren(new HashSet<>(Set.of(missingChild)));
            source.setParents(null);

            RecordingUnit managed = new RecordingUnit();
            managed.setId(1L);
            managed.setFullIdentifier("MANAGED-1");
            managed.setChildren(new HashSet<>());

            when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(source);
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
            when(recordingUnitRepository.findAllById(any())).thenReturn(List.of());
            when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThrows(FailedRecordingUnitSaveException.class,
                    () -> recordingUnitService.save(new RecordingUnitDTO()));
        }

        @Test
        void save_setupParents_addsNewParentAndRemovesStaleParent() {
            RecordingUnit newParent = new RecordingUnit();
            newParent.setId(2L);
            newParent.setFullIdentifier("NEW-PARENT");
            newParent.setChildren(new HashSet<>());

            RecordingUnit staleParent = new RecordingUnit();
            staleParent.setId(3L);
            staleParent.setFullIdentifier("STALE-PARENT");
            staleParent.setChildren(new HashSet<>());

            RecordingUnit incomingParent = new RecordingUnit();
            incomingParent.setId(2L);

            RecordingUnit source = new RecordingUnit();
            source.setId(1L);
            source.setFullIdentifier("SRC-1");
            source.setParents(new HashSet<>(Set.of(incomingParent)));
            source.setChildren(null);

            RecordingUnit managed = new RecordingUnit();
            managed.setId(1L);
            managed.setFullIdentifier("MANAGED-1");
            managed.setParents(new HashSet<>(Set.of(staleParent)));

            staleParent.getChildren().add(managed);

            when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(source);
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
            when(recordingUnitRepository.findAllById(argThat(ids -> {
                List<Long> collected = new ArrayList<>();
                ids.forEach(collected::add);
                return collected.equals(List.of(2L));
            }))).thenReturn(List.of(newParent));
            when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordingUnitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(new RecordingUnitDTO());

            recordingUnitService.save(new RecordingUnitDTO());

            assertEquals(Set.of(newParent), managed.getParents());
            assertTrue(newParent.getChildren().contains(managed));
            assertFalse(staleParent.getChildren().contains(managed));
        }

        @Test
        void save_setupParents_nullParents_isNoop() {
            RecordingUnit source = new RecordingUnit();
            source.setId(1L);
            source.setFullIdentifier("SRC-1");
            source.setParents(null);
            source.setChildren(null);

            RecordingUnit managed = new RecordingUnit();
            managed.setId(1L);
            managed.setFullIdentifier("MANAGED-1");

            when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(source);
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
            when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));
            when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(new RecordingUnitDTO());

            recordingUnitService.save(new RecordingUnitDTO());

            verify(recordingUnitRepository, never()).saveAll(anyList());
        }

        @Test
        void save_setupParents_missingParentThrows_wrappedAsFailedRecordingUnitSaveException() {
            RecordingUnit missingParent = new RecordingUnit();
            missingParent.setId(77L);

            RecordingUnit source = new RecordingUnit();
            source.setId(1L);
            source.setFullIdentifier("SRC-1");
            source.setParents(new HashSet<>(Set.of(missingParent)));
            source.setChildren(null);

            RecordingUnit managed = new RecordingUnit();
            managed.setId(1L);
            managed.setFullIdentifier("MANAGED-1");

            when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(source);
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
            when(recordingUnitRepository.findAllById(any())).thenReturn(List.of());
            when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThrows(FailedRecordingUnitSaveException.class,
                    () -> recordingUnitService.save(new RecordingUnitDTO()));
        }
    }

    // =====================================================================
    // deleteRecordingUnitById and its helpers
    // =====================================================================

    @Nested
    class DeleteRecordingUnitByIdTests {

        @Test
        void deleteRecordingUnitById_notFound_throwsRecordingUnitNotFoundException() {
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.deleteRecordingUnitById(1L));
        }

        @Test
        void deleteRecordingUnitById_hasSpecimens_throwsIllegalState() {
            RecordingUnit ru = new RecordingUnit();
            ru.setId(1L);
            ru.setFullIdentifier("RU-1");

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(ru));
            when(recordingUnitRepository.countSpecimensByRecordingUnitId(1L)).thenReturn(3L);

            assertThrows(IllegalStateException.class,
                    () -> recordingUnitService.deleteRecordingUnitById(1L));
        }

        @Test
        void deleteRecordingUnitById_hasChildren_throwsIllegalState() {
            RecordingUnit ru = new RecordingUnit();
            ru.setId(1L);
            ru.setFullIdentifier("RU-1");
            RecordingUnit child = new RecordingUnit();
            child.setId(2L);
            child.setFullIdentifier("RU-2");
            ru.setChildren(new HashSet<>(Set.of(child)));

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(ru));
            when(recordingUnitRepository.countSpecimensByRecordingUnitId(1L)).thenReturn(0L);

            assertThrows(IllegalStateException.class,
                    () -> recordingUnitService.deleteRecordingUnitById(1L));
        }

        @Test
        void deleteRecordingUnitById_happyPath_deletesAllLinkedDataAndTheUnit() {
            RecordingUnit ru = new RecordingUnit();
            ru.setId(1L);
            ru.setFullIdentifier("RU-1");

            RecordingUnit parent = new RecordingUnit();
            parent.setId(2L);
            parent.setFullIdentifier("RU-2");
            parent.setChildren(new HashSet<>(Set.of(ru)));
            ru.setParents(new HashSet<>(Set.of(parent)));

            StratigraphicRelationship rel = new StratigraphicRelationship();
            ru.setRelationshipsAsUnit1(new HashSet<>(Set.of(rel)));

            CustomFormResponse formResponse = new CustomFormResponse();
            formResponse.setId(50L);
            ru.setFormResponse(formResponse);

            Ark ark = new Ark();
            ark.setInternalId(99L);
            ru.setArk(ark);

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(ru));
            when(recordingUnitRepository.countSpecimensByRecordingUnitId(1L)).thenReturn(0L);
            when(stratigraphicRelationshipRepository.findAllInvolvingRecordingUnitId(1L)).thenReturn(List.of(rel));
            when(recordingUnitIdInfoRepository.existsById(1L)).thenReturn(true);
            when(recordingUnitRepository.save(ru)).thenReturn(ru);

            recordingUnitService.deleteRecordingUnitById(1L);

            assertFalse(parent.getChildren().contains(ru));
            assertTrue(ru.getParents().isEmpty());
            assertTrue(ru.getRelationshipsAsUnit1().isEmpty());
            verify(stratigraphicRelationshipRepository).deleteAll(List.of(rel));
            verify(recordingUnitRepository).deleteContributorLinksForRecordingUnit(1L);
            verify(documentRepository).deleteAllRecordingUnitDocumentLinksByRecordingUnitId(1L);
            verify(recordingUnitIdCounterRepository).deleteAllByRecordingUnitId(1L);
            verify(recordingUnitIdInfoRepository).deleteById(1L);
            verify(customFormResponseRepository).deleteById(50L);
            verify(recordingUnitRepository).delete(ru);
            verify(arkRepository).deleteById(99L);
        }

        @Test
        void deleteRecordingUnitById_noFormResponseNoArkNoIdInfo_skipsOptionalDeletes() {
            RecordingUnit ru = new RecordingUnit();
            ru.setId(5L);
            ru.setFullIdentifier("RU-5");

            when(recordingUnitRepository.findById(5L)).thenReturn(Optional.of(ru));
            when(recordingUnitRepository.countSpecimensByRecordingUnitId(5L)).thenReturn(0L);
            when(stratigraphicRelationshipRepository.findAllInvolvingRecordingUnitId(5L)).thenReturn(List.of());
            when(recordingUnitIdInfoRepository.existsById(5L)).thenReturn(false);
            when(recordingUnitRepository.save(ru)).thenReturn(ru);

            recordingUnitService.deleteRecordingUnitById(5L);

            verify(stratigraphicRelationshipRepository, never()).deleteAll(anyList());
            verify(recordingUnitIdInfoRepository, never()).deleteById(any());
            verify(customFormResponseRepository, never()).deleteById(any());
            verify(arkRepository, never()).deleteById(any());
            verify(recordingUnitRepository).delete(ru);
        }
    }

    // =====================================================================
    // addHierarchyChild / removeHierarchyChild / assertSameActionUnit /
    // wouldCreateHierarchyCycle
    // =====================================================================

    @Nested
    class HierarchyChildTests {

        @Test
        void addHierarchyChild_sameId_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> recordingUnitService.addHierarchyChild(1L, 1L));
        }

        @Test
        void addHierarchyChild_parentNotFound_throws() {
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.addHierarchyChild(1L, 2L));
        }

        @Test
        void addHierarchyChild_childNotFound_throws() {
            RecordingUnit parent = new RecordingUnit();
            parent.setId(1L);
            parent.setFullIdentifier("P");

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(recordingUnitRepository.findById(2L)).thenReturn(Optional.empty());

            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.addHierarchyChild(1L, 2L));
        }

        @Test
        void addHierarchyChild_differentActionUnits_throwsIllegalArgument() {
            ActionUnit a1 = new ActionUnit();
            a1.setId(10L);
            ActionUnit a2 = new ActionUnit();
            a2.setId(20L);

            RecordingUnit parent = new RecordingUnit();
            parent.setId(1L);
            parent.setFullIdentifier("P");
            parent.setActionUnit(a1);
            RecordingUnit child = new RecordingUnit();
            child.setId(2L);
            child.setFullIdentifier("C");
            child.setActionUnit(a2);

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(child));

            assertThrows(IllegalArgumentException.class,
                    () -> recordingUnitService.addHierarchyChild(1L, 2L));
        }

        @Test
        void addHierarchyChild_relationAlreadyExists_throwsIllegalState() {
            ActionUnit a1 = new ActionUnit();
            a1.setId(10L);

            RecordingUnit parent = new RecordingUnit();
            parent.setId(1L);
            parent.setFullIdentifier("P");
            parent.setActionUnit(a1);
            RecordingUnit child = new RecordingUnit();
            child.setId(2L);
            child.setFullIdentifier("C");
            child.setActionUnit(a1);
            parent.setChildren(new HashSet<>(Set.of(child)));

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(child));

            assertThrows(IllegalStateException.class,
                    () -> recordingUnitService.addHierarchyChild(1L, 2L));
        }

        @Test
        void addHierarchyChild_wouldCreateCycle_throwsIllegalState() {
            ActionUnit a1 = new ActionUnit();
            a1.setId(10L);

            RecordingUnit parent = new RecordingUnit();
            parent.setId(1L);
            parent.setFullIdentifier("P");
            parent.setActionUnit(a1);
            parent.setChildren(new HashSet<>());
            RecordingUnit child = new RecordingUnit();
            child.setId(2L);
            child.setFullIdentifier("C");
            child.setActionUnit(a1);

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(child));
            when(recordingUnitRepository.findAncestorClosure(new Long[]{1L})).thenReturn(List.of(1L, 2L));

            assertThrows(IllegalStateException.class,
                    () -> recordingUnitService.addHierarchyChild(1L, 2L));
        }

        @Test
        void addHierarchyChild_success_addsRelationAndSavesParent() {
            ActionUnit a1 = new ActionUnit();
            a1.setId(10L);

            RecordingUnit parent = new RecordingUnit();
            parent.setId(1L);
            parent.setFullIdentifier("P");
            parent.setActionUnit(a1);
            parent.setChildren(new HashSet<>());
            RecordingUnit child = new RecordingUnit();
            child.setId(2L);
            child.setFullIdentifier("C");
            child.setActionUnit(a1);
            child.setParents(new HashSet<>());

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(child));
            when(recordingUnitRepository.findAncestorClosure(new Long[]{1L})).thenReturn(List.of(1L));
            when(recordingUnitRepository.save(parent)).thenReturn(parent);

            recordingUnitService.addHierarchyChild(1L, 2L);

            assertTrue(parent.getChildren().contains(child));
            assertTrue(child.getParents().contains(parent));
            verify(recordingUnitRepository).save(parent);
        }

        @Test
        void removeHierarchyChild_parentNotFound_throws() {
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.removeHierarchyChild(1L, 2L));
        }

        @Test
        void removeHierarchyChild_childNotFound_throws() {
            RecordingUnit parent = new RecordingUnit();
            parent.setId(1L);
            parent.setFullIdentifier("P");

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(recordingUnitRepository.findById(2L)).thenReturn(Optional.empty());

            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.removeHierarchyChild(1L, 2L));
        }

        @Test
        void removeHierarchyChild_noExistingRelation_throwsIllegalState() {
            RecordingUnit parent = new RecordingUnit();
            parent.setId(1L);
            parent.setFullIdentifier("P");
            parent.setChildren(new HashSet<>());
            RecordingUnit child = new RecordingUnit();
            child.setId(2L);
            child.setFullIdentifier("C");

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(child));

            assertThrows(IllegalStateException.class,
                    () -> recordingUnitService.removeHierarchyChild(1L, 2L));
        }

        @Test
        void removeHierarchyChild_success_removesRelationAndSavesParent() {
            RecordingUnit parent = new RecordingUnit();
            parent.setId(1L);
            parent.setFullIdentifier("P");
            RecordingUnit child = new RecordingUnit();
            child.setId(2L);
            child.setFullIdentifier("C");
            parent.setChildren(new HashSet<>(Set.of(child)));
            child.setParents(new HashSet<>(Set.of(parent)));

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(parent));
            when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(child));
            when(recordingUnitRepository.save(parent)).thenReturn(parent);

            recordingUnitService.removeHierarchyChild(1L, 2L);

            assertFalse(parent.getChildren().contains(child));
            assertFalse(child.getParents().contains(parent));
            verify(recordingUnitRepository).save(parent);
        }
    }

    // =====================================================================
    // toAdjacentUnitSummaries / resolveRecordingUnitEntityByKey /
    // resolveByNumericKey / resolveRecordingUnitEntityByFullIdentifier
    // =====================================================================

    @Nested
    class ResolveKeyAndAdjacentUnitsTests {

        @Test
        void findChildrenForAccessibleRecordingUnit_noChildren_returnsEmptyList() {
            RecordingUnit unit = new RecordingUnit();
            unit.setId(1L);
            unit.setFullIdentifier("RU-1");
            unit.setChildren(new HashSet<>());

            Set<Long> accessible = Set.of(100L);
            RecordingUnitDTO convertedDto = new RecordingUnitDTO();
            InstitutionDTO instDto = new InstitutionDTO();
            instDto.setId(100L);
            convertedDto.setCreatedByInstitution(instDto);

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(unit));
            when(recordingUnitMapper.convert(unit)).thenReturn(convertedDto);

            List<RecordingUnitSummaryDTO> result =
                    recordingUnitService.findChildrenForAccessibleRecordingUnit("1", accessible);

            assertTrue(result.isEmpty());
        }

        @Test
        void findChildrenForAccessibleRecordingUnit_withChildren_returnsSortedSummaries() {
            RecordingUnit child1 = new RecordingUnit();
            child1.setId(5L);
            child1.setFullIdentifier("C5");
            RecordingUnit child2 = new RecordingUnit();
            child2.setId(3L);
            child2.setFullIdentifier("C3");

            RecordingUnit unit = new RecordingUnit();
            unit.setId(1L);
            unit.setFullIdentifier("RU-1");
            unit.setChildren(new HashSet<>(Set.of(child1, child2)));

            Set<Long> accessible = Set.of(100L);
            RecordingUnitDTO convertedDto = new RecordingUnitDTO();
            InstitutionDTO instDto = new InstitutionDTO();
            instDto.setId(100L);
            convertedDto.setCreatedByInstitution(instDto);

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(unit));
            when(recordingUnitMapper.convert(unit)).thenReturn(convertedDto);

            RecordingUnitSummaryDTO summary1 = new RecordingUnitSummaryDTO();
            summary1.setId(5L);
            RecordingUnitSummaryDTO summary2 = new RecordingUnitSummaryDTO();
            summary2.setId(3L);
            when(recordingUnitSummaryMapper.convert(child1)).thenReturn(summary1);
            when(recordingUnitSummaryMapper.convert(child2)).thenReturn(summary2);

            List<RecordingUnitSummaryDTO> result =
                    recordingUnitService.findChildrenForAccessibleRecordingUnit("1", accessible);

            assertEquals(List.of(summary2, summary1), result);
        }

        @Test
        void findAccessibleRecordingUnitByKey_emptyAccessibleInstitutions_throws() {
            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.findAccessibleRecordingUnitByKey("1", Set.of(), null));
        }

        @Test
        void findAccessibleRecordingUnitByKey_blankKey_throws() {
            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.findAccessibleRecordingUnitByKey("   ", Set.of(100L), null));
        }

        @Test
        void findAccessibleRecordingUnitByKey_numericKey_notAccessible_throws() {
            RecordingUnit unit = new RecordingUnit();
            unit.setId(1L);
            unit.setFullIdentifier("RU-1");
            RecordingUnitDTO convertedDto = new RecordingUnitDTO();
            InstitutionDTO instDto = new InstitutionDTO();
            instDto.setId(999L);
            convertedDto.setCreatedByInstitution(instDto);

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(unit));
            when(recordingUnitMapper.convert(unit)).thenReturn(convertedDto);

            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.findAccessibleRecordingUnitByKey("1", Set.of(100L), null));
        }

        @Test
        void findAccessibleRecordingUnitByKey_numericKey_notFoundById_fallsBackToFullIdentifier() {
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.empty());

            RecordingUnit found = new RecordingUnit();
            found.setId(2L);
            found.setFullIdentifier("1");
            when(recordingUnitRepository.findFirstByFullIdentifierAndInstitutionIdIn("1", Set.of(100L)))
                    .thenReturn(Optional.of(found));
            when(recordingUnitMapper.convert(found)).thenReturn(new RecordingUnitDTO());

            RecordingUnitDTO result =
                    recordingUnitService.findAccessibleRecordingUnitByKey("1", Set.of(100L), null);

            assertNotNull(result);
            verify(recordingUnitRepository).findFirstByFullIdentifierAndInstitutionIdIn("1", Set.of(100L));
        }

        @Test
        void findAccessibleRecordingUnitByKey_numericOverflow_fallsBackToFullIdentifier() {
            String hugeKey = "99999999999999999999";
            RecordingUnit found = new RecordingUnit();
            found.setId(3L);
            found.setFullIdentifier(hugeKey);
            when(recordingUnitRepository.findFirstByFullIdentifierAndInstitutionIdIn(hugeKey, Set.of(100L)))
                    .thenReturn(Optional.of(found));
            when(recordingUnitMapper.convert(found)).thenReturn(new RecordingUnitDTO());

            RecordingUnitDTO result =
                    recordingUnitService.findAccessibleRecordingUnitByKey(hugeKey, Set.of(100L), null);

            assertNotNull(result);
            verify(recordingUnitRepository, never()).findById(anyLong());
        }

        @Test
        void findAccessibleRecordingUnitByKey_nonNumericKey_usesFullIdentifierLookup() {
            RecordingUnit found = new RecordingUnit();
            found.setId(4L);
            found.setFullIdentifier("ABC-1");
            when(recordingUnitRepository.findFirstByFullIdentifierAndInstitutionIdIn("ABC-1", Set.of(100L)))
                    .thenReturn(Optional.of(found));
            when(recordingUnitMapper.convert(found)).thenReturn(new RecordingUnitDTO());

            RecordingUnitDTO result =
                    recordingUnitService.findAccessibleRecordingUnitByKey("ABC-1", Set.of(100L), null);

            assertNotNull(result);
        }

        @Test
        void findAccessibleRecordingUnitByKey_fullIdentifierNotFound_throws() {
            when(recordingUnitRepository.findFirstByFullIdentifierAndInstitutionIdIn("ABC-1", Set.of(100L)))
                    .thenReturn(Optional.empty());

            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.findAccessibleRecordingUnitByKey("ABC-1", Set.of(100L), null));
        }

        @Test
        void findAccessibleRecordingUnitByKey_numericKey_withSpecimenCount() {
            RecordingUnit unit = new RecordingUnit();
            unit.setId(1L);
            unit.setFullIdentifier("RU-1");
            RecordingUnitDTO convertedDto = new RecordingUnitDTO();
            convertedDto.setId(1L);
            InstitutionDTO instDto = new InstitutionDTO();
            instDto.setId(100L);
            convertedDto.setCreatedByInstitution(instDto);

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(unit));
            when(recordingUnitMapper.convert(unit)).thenReturn(convertedDto);
            when(recordingUnitRepository.countSpecimensByRecordingUnitId(1L)).thenReturn(7L);

            RecordingUnitDTO result = recordingUnitService.findAccessibleRecordingUnitByKey(
                    "1", Set.of(100L), List.of("specimen"));

            assertEquals(7L, result.getSpecimenCount());
        }
    }

    // =====================================================================
    // findNextByActionUnit / findPreviousByActionUnit
    // =====================================================================

    @Nested
    class NextAndPreviousByActionUnitTests {

        @Test
        void findNextByActionUnit_hasNext_returnsConvertedNext() {
            ActionUnitSummaryDTO action = new ActionUnitSummaryDTO();
            action.setId(10L);
            RecordingUnitDTO current = new RecordingUnitDTO();
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
            current.setCreationTime(now);

            RecordingUnit next = new RecordingUnit();
            next.setId(2L);
            next.setFullIdentifier("NEXT");
            RecordingUnitDTO nextDto = new RecordingUnitDTO();

            when(recordingUnitRepository.findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(10L, now))
                    .thenReturn(Optional.of(next));
            when(recordingUnitMapper.convert(next)).thenReturn(nextDto);

            RecordingUnitDTO result = recordingUnitService.findNextByActionUnit(action, current);

            assertSame(nextDto, result);
            verify(recordingUnitRepository, never()).findFirstByActionUnitIdOrderByCreationTimeAsc(anyLong());
        }

        @Test
        void findNextByActionUnit_noNext_wrapsToOldest() {
            ActionUnitSummaryDTO action = new ActionUnitSummaryDTO();
            action.setId(10L);
            RecordingUnitDTO current = new RecordingUnitDTO();
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
            current.setCreationTime(now);

            RecordingUnit oldest = new RecordingUnit();
            oldest.setId(3L);
            oldest.setFullIdentifier("OLDEST");
            RecordingUnitDTO oldestDto = new RecordingUnitDTO();

            when(recordingUnitRepository.findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(10L, now))
                    .thenReturn(Optional.empty());
            when(recordingUnitRepository.findFirstByActionUnitIdOrderByCreationTimeAsc(10L))
                    .thenReturn(Optional.of(oldest));
            when(recordingUnitMapper.convert(oldest)).thenReturn(oldestDto);

            RecordingUnitDTO result = recordingUnitService.findNextByActionUnit(action, current);

            assertSame(oldestDto, result);
        }

        @Test
        void findNextByActionUnit_noNextNoOldest_throwsActionUnitNotFound() {
            ActionUnitSummaryDTO action = new ActionUnitSummaryDTO();
            action.setId(10L);
            RecordingUnitDTO current = new RecordingUnitDTO();
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
            current.setCreationTime(now);

            when(recordingUnitRepository.findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(10L, now))
                    .thenReturn(Optional.empty());
            when(recordingUnitRepository.findFirstByActionUnitIdOrderByCreationTimeAsc(10L))
                    .thenReturn(Optional.empty());

            assertThrows(ActionUnitNotFoundException.class,
                    () -> recordingUnitService.findNextByActionUnit(action, current));
        }

        @Test
        void findPreviousByActionUnit_hasPrevious_returnsConvertedPrevious() {
            ActionUnitSummaryDTO action = new ActionUnitSummaryDTO();
            action.setId(10L);
            RecordingUnitDTO current = new RecordingUnitDTO();
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
            current.setCreationTime(now);

            RecordingUnit previous = new RecordingUnit();
            previous.setId(2L);
            previous.setFullIdentifier("PREVIOUS");
            RecordingUnitDTO previousDto = new RecordingUnitDTO();

            when(recordingUnitRepository.findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(10L, now))
                    .thenReturn(Optional.of(previous));
            when(recordingUnitMapper.convert(previous)).thenReturn(previousDto);

            RecordingUnitDTO result = recordingUnitService.findPreviousByActionUnit(action, current);

            assertSame(previousDto, result);
            verify(recordingUnitRepository, never()).findFirstByActionUnitIdOrderByCreationTimeDesc(anyLong());
        }

        @Test
        void findPreviousByActionUnit_noPrevious_wrapsToNewest() {
            ActionUnitSummaryDTO action = new ActionUnitSummaryDTO();
            action.setId(10L);
            RecordingUnitDTO current = new RecordingUnitDTO();
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
            current.setCreationTime(now);

            RecordingUnit newest = new RecordingUnit();
            newest.setId(3L);
            newest.setFullIdentifier("NEWEST");
            RecordingUnitDTO newestDto = new RecordingUnitDTO();

            when(recordingUnitRepository.findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(10L, now))
                    .thenReturn(Optional.empty());
            when(recordingUnitRepository.findFirstByActionUnitIdOrderByCreationTimeDesc(10L))
                    .thenReturn(Optional.of(newest));
            when(recordingUnitMapper.convert(newest)).thenReturn(newestDto);

            RecordingUnitDTO result = recordingUnitService.findPreviousByActionUnit(action, current);

            assertSame(newestDto, result);
        }

        @Test
        void findPreviousByActionUnit_noPreviousNoNewest_throwsActionUnitNotFound() {
            ActionUnitSummaryDTO action = new ActionUnitSummaryDTO();
            action.setId(10L);
            RecordingUnitDTO current = new RecordingUnitDTO();
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
            current.setCreationTime(now);

            when(recordingUnitRepository.findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(10L, now))
                    .thenReturn(Optional.empty());
            when(recordingUnitRepository.findFirstByActionUnitIdOrderByCreationTimeDesc(10L))
                    .thenReturn(Optional.empty());

            assertThrows(ActionUnitNotFoundException.class,
                    () -> recordingUnitService.findPreviousByActionUnit(action, current));
        }
    }

    // =====================================================================
    // toggleValidated / findAllByParentRecordingUnit
    // =====================================================================

    @Nested
    class ToggleValidatedAndParentChildrenTests {

        @Test
        void toggleValidated_incompleteBecomesComplete() {
            RecordingUnit unit = new RecordingUnit();
            unit.setId(1L);
            unit.setFullIdentifier("RU-1");
            unit.setValidated(ValidationStatus.INCOMPLETE);

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(unit));
            when(recordingUnitRepository.save(unit)).thenReturn(unit);
            when(recordingUnitMapper.convert(unit)).thenReturn(new RecordingUnitDTO());

            recordingUnitService.toggleValidated(1L);

            assertEquals(ValidationStatus.COMPLETE, unit.getValidated());
        }

        @Test
        void toggleValidated_completeBecomesValidated() {
            RecordingUnit unit = new RecordingUnit();
            unit.setId(1L);
            unit.setFullIdentifier("RU-1");
            unit.setValidated(ValidationStatus.COMPLETE);

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(unit));
            when(recordingUnitRepository.save(unit)).thenReturn(unit);
            when(recordingUnitMapper.convert(unit)).thenReturn(new RecordingUnitDTO());

            recordingUnitService.toggleValidated(1L);

            assertEquals(ValidationStatus.VALIDATED, unit.getValidated());
        }

        @Test
        void toggleValidated_validatedBecomesIncomplete() {
            RecordingUnit unit = new RecordingUnit();
            unit.setId(1L);
            unit.setFullIdentifier("RU-1");
            unit.setValidated(ValidationStatus.VALIDATED);

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(unit));
            when(recordingUnitRepository.save(unit)).thenReturn(unit);
            when(recordingUnitMapper.convert(unit)).thenReturn(new RecordingUnitDTO());

            recordingUnitService.toggleValidated(1L);

            assertEquals(ValidationStatus.INCOMPLETE, unit.getValidated());
        }

        @Test
        void toggleValidated_notFound_throws() {
            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.toggleValidated(1L));
        }

        @Test
        void findAllByParentRecordingUnit_returnsMappedChildren() {
            RecordingUnit child = new RecordingUnit();
            child.setId(2L);
            child.setFullIdentifier("C");
            RecordingUnitDTO childDto = new RecordingUnitDTO();

            when(recordingUnitRepository.findChildrensOf(1L)).thenReturn(List.of(child));
            when(recordingUnitMapper.convert(child)).thenReturn(childDto);

            List<RecordingUnitDTO> result = recordingUnitService.findAllByParentRecordingUnit(1L);

            assertEquals(List.of(childDto), result);
        }
    }

    // =====================================================================
    // searchRecordingUnit / searchRecordingUnitInActionUnit /
    // searchRecordingUnitInSpatialUnit / countSearchResults family
    // =====================================================================

    @Nested
    class SearchAndCountFamilyTests {

        @Test
        void searchRecordingUnit_populatesParentsChildrenAndPhases() {
            InstitutionDTO institution = new InstitutionDTO();
            institution.setId(1L);
            FilterDTO filters = new FilterDTO(false);
            Pageable pageable = PageRequest.of(0, 10);

            RecordingUnit ru = new RecordingUnit();
            ru.setId(7L);
            ru.setFullIdentifier("RU-7");
            RecordingUnitDTO ruDto = new RecordingUnitDTO();
            ruDto.setId(7L);

            RecordingUnit parent = new RecordingUnit();
            parent.setId(8L);
            parent.setFullIdentifier("P8");
            RecordingUnit child = new RecordingUnit();
            child.setId(9L);
            child.setFullIdentifier("C9");
            Phase phase = new Phase();
            phase.setId(11L);
            PhaseDTO phaseDto = new PhaseDTO();

            when(recordingUnitRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(ru)));
            when(recordingUnitMapper.toLightDto(ru)).thenReturn(ruDto);
            when(recordingUnitRepository.findParentsOf(7L)).thenReturn(Set.of(parent));
            when(recordingUnitRepository.findChildrensOf(7L)).thenReturn(List.of(child));
            when(phaseRepository.findByRecordingUnitId(7L)).thenReturn(Set.of(phase));

            RecordingUnitSummaryDTO parentSummary = new RecordingUnitSummaryDTO();
            parentSummary.setId(8L);
            RecordingUnitSummaryDTO childSummary = new RecordingUnitSummaryDTO();
            childSummary.setId(9L);
            when(recordingUnitSummaryMapper.convert(parent)).thenReturn(parentSummary);
            when(recordingUnitSummaryMapper.convert(child)).thenReturn(childSummary);
            when(phaseMapper.convert(phase)).thenReturn(phaseDto);

            Page<RecordingUnitDTO> result = recordingUnitService.searchRecordingUnit(institution, filters, pageable);

            assertEquals(1, result.getTotalElements());
            RecordingUnitDTO dto = result.getContent().get(0);
            assertEquals(Set.of(parentSummary), dto.getParents());
            assertEquals(Set.of(childSummary), dto.getChildren());
            assertEquals(Set.of(phaseDto), dto.getPhases());
        }

        @Test
        void searchRecordingUnitInActionUnit_delegatesToRepositoryAndMaps() {
            InstitutionDTO institution = new InstitutionDTO();
            institution.setId(1L);
            ActionUnitDTO actionUnit = new ActionUnitDTO();
            actionUnit.setId(20L);
            FilterDTO filters = new FilterDTO(false);
            Pageable pageable = PageRequest.of(0, 10);

            RecordingUnit ru = new RecordingUnit();
            ru.setId(7L);
            ru.setFullIdentifier("RU-7");
            RecordingUnitDTO ruDto = new RecordingUnitDTO();

            when(recordingUnitRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(ru)));
            when(recordingUnitMapper.convert(ru)).thenReturn(ruDto);

            Page<RecordingUnitDTO> result =
                    recordingUnitService.searchRecordingUnitInActionUnit(institution, actionUnit, filters, pageable);

            assertEquals(1, result.getTotalElements());
            assertSame(ruDto, result.getContent().get(0));
        }

        @Test
        void searchRecordingUnitInSpatialUnit_delegatesToRepositoryAndMaps() {
            InstitutionDTO institution = new InstitutionDTO();
            institution.setId(1L);
            SpatialUnitDTO spatialUnit = new SpatialUnitDTO();
            spatialUnit.setId(30L);
            FilterDTO filters = new FilterDTO(false);
            Pageable pageable = PageRequest.of(0, 10);

            RecordingUnit ru = new RecordingUnit();
            ru.setId(7L);
            ru.setFullIdentifier("RU-7");
            RecordingUnitDTO ruDto = new RecordingUnitDTO();

            when(recordingUnitRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(ru)));
            when(recordingUnitMapper.convert(ru)).thenReturn(ruDto);

            Page<RecordingUnitDTO> result =
                    recordingUnitService.searchRecordingUnitInSpatialUnit(institution, spatialUnit, filters, pageable);

            assertEquals(1, result.getTotalElements());
            assertSame(ruDto, result.getContent().get(0));
        }

        @Test
        void countSearchResultsInActionUnit_returnsRepositoryCount() {
            InstitutionDTO institution = new InstitutionDTO();
            institution.setId(1L);
            ActionUnitDTO actionUnit = new ActionUnitDTO();
            actionUnit.setId(20L);
            FilterDTO filters = new FilterDTO(false);

            when(recordingUnitRepository.count(any(Specification.class))).thenReturn(4L);

            int result = recordingUnitService.countSearchResultsInActionUnit(institution, actionUnit, filters);

            assertEquals(4, result);
        }

        @Test
        void countSearchResultsInSpatialUnit_returnsRepositoryCount() {
            InstitutionDTO institution = new InstitutionDTO();
            institution.setId(1L);
            SpatialUnitDTO spatialUnit = new SpatialUnitDTO();
            spatialUnit.setId(30L);
            FilterDTO filters = new FilterDTO(false);

            when(recordingUnitRepository.count(any(Specification.class))).thenReturn(6L);

            int result = recordingUnitService.countSearchResultsInSpatialUnit(institution, spatialUnit, filters);

            assertEquals(6, result);
        }

        @Test
        void countSearchResults_returnsRepositoryCount() {
            InstitutionDTO institution = new InstitutionDTO();
            institution.setId(1L);
            FilterDTO filters = new FilterDTO(false);

            when(recordingUnitRepository.count(any(Specification.class))).thenReturn(9L);

            int result = recordingUnitService.countSearchResults(institution, filters);

            assertEquals(9, result);
        }
    }

    // =====================================================================
    // prepareSpecs / userFilterSpecs / resolveAncestorClosure /
    // computeAncestorClosure / initializeHierarchy
    // =====================================================================

    @Nested
    class SpecsAndClosureAndHierarchyTests {

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
            filters.add(RecordingUnitSpec.OPENING_DATE_FILTER,
                    List.of(java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now()),
                    FilterDTO.FilterType.EQUAL);
            filters.add(RecordingUnitSpec.CLOSING_DATE_FILTER,
                    List.of(java.time.OffsetDateTime.now()), FilterDTO.FilterType.EQUAL);
            filters.add(RecordingUnitSpec.PARENTS_FILTER, List.of(6L), FilterDTO.FilterType.EQUAL);

            Specification<RecordingUnit> spec = RecordingUnitService.userFilterSpecs(filters);

            assertNotNull(spec);
        }

        @Test
        void prepareSpecs_rootOnlyNoUserFilters_returnsRootSpecWithoutRepositoryCall() {
            InstitutionDTO institution = new InstitutionDTO();
            institution.setId(1L);
            FilterDTO filters = new FilterDTO(true);

            Specification<RecordingUnit> spec = recordingUnitService.prepareSpecs(institution, filters);

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

            Specification<RecordingUnit> spec = recordingUnitService.prepareSpecs(institution, filters);

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

            Specification<RecordingUnit> spec = recordingUnitService.prepareSpecs(institution, filters);

            assertNotNull(spec);
        }

        @Test
        void prepareSpecs_notRootOnly_returnsUserFilterSpecWithoutRepositoryCall() {
            InstitutionDTO institution = new InstitutionDTO();
            institution.setId(1L);
            FilterDTO filters = new FilterDTO(false);

            Specification<RecordingUnit> spec = recordingUnitService.prepareSpecs(institution, filters);

            assertNotNull(spec);
            verifyNoInteractions(recordingUnitRepository);
        }

        @Test
        void computeAncestorClosure_notRootOnly_returnsEmptySet() {
            InstitutionDTO institution = new InstitutionDTO();
            institution.setId(1L);
            FilterDTO filters = new FilterDTO(false);
            filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(9L), FilterDTO.FilterType.EQUAL);

            Set<Long> result = recordingUnitService.computeAncestorClosure(institution, filters);

            assertTrue(result.isEmpty());
            verifyNoInteractions(recordingUnitRepository);
        }

        @Test
        void computeAncestorClosure_rootOnlyNoUserFilters_returnsEmptySet() {
            InstitutionDTO institution = new InstitutionDTO();
            institution.setId(1L);
            FilterDTO filters = new FilterDTO(true);

            Set<Long> result = recordingUnitService.computeAncestorClosure(institution, filters);

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

            Set<Long> result = recordingUnitService.computeAncestorClosure(institution, filters);

            assertEquals(Set.of(42L, 1L), result);
        }

        @Test
        void initializeHierarchy_setsParentsAndChildrenFromRepository() {
            RecordingUnitDTO dto = new RecordingUnitDTO();
            dto.setId(7L);

            RecordingUnit parent = new RecordingUnit();
            parent.setId(8L);
            parent.setFullIdentifier("P8");
            RecordingUnit child = new RecordingUnit();
            child.setId(9L);
            child.setFullIdentifier("C9");

            RecordingUnitSummaryDTO parentSummary = new RecordingUnitSummaryDTO();
            parentSummary.setId(8L);
            RecordingUnitSummaryDTO childSummary = new RecordingUnitSummaryDTO();
            childSummary.setId(9L);

            when(recordingUnitRepository.findParentsOf(7L)).thenReturn(Set.of(parent));
            when(recordingUnitRepository.findChildrensOf(7L)).thenReturn(List.of(child));
            when(recordingUnitSummaryMapper.convert(parent)).thenReturn(parentSummary);
            when(recordingUnitSummaryMapper.convert(child)).thenReturn(childSummary);

            recordingUnitService.initializeHierarchy(dto);

            assertEquals(Set.of(parentSummary), dto.getParents());
            assertEquals(Set.of(childSummary), dto.getChildren());
        }
    }
}
