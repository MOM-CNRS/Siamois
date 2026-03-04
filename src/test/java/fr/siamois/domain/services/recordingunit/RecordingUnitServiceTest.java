package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.CustomFormResponseService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdCounterRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdInfoRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;

import java.util.*;


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
    private CustomFormResponseService customFormResponseService;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private InstitutionService institutionService;

    @Mock
    private ActionUnitService actionUnitService;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private RecordingUnitIdCounterRepository recordingUnitIdCounterRepository;

    @Mock
    private RecordingUnitIdInfoRepository recordingUnitIdInfoRepository;

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
        when(conversionService.convert(any(RecordingUnitDTO.class), eq(RecordingUnit.class))).thenReturn(recordingUnit);
        // Mock the save method
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenReturn(recordingUnit);
        // Mock the conversion from entity to DTO (return a dummy DTO)
        when(conversionService.convert(any(RecordingUnit.class), eq(RecordingUnitDTO.class))).thenReturn(new RecordingUnitDTO());

        // Act
        recordingUnitService.save(new RecordingUnitDTO());

        // Assert: Capture the argument passed to the last convert call
        ArgumentCaptor<RecordingUnit> entityCaptor = forClass(RecordingUnit.class);
        verify(conversionService).convert(entityCaptor.capture(), eq(RecordingUnitDTO.class));

        RecordingUnit savedEntity = entityCaptor.getValue();
        assertNotNull(savedEntity);

    }

    @Test
    void countByInstitution_success() {
        when(recordingUnitRepository.countByCreatedByInstitutionId(3L)).thenReturn(3L);
        assertEquals(3,recordingUnitService.countByInstitutionId(3L));
    }

    @Test
    void save_SyncsStratigraphicRelationshipsAsUnit1() {
        // Arrange

        // DTO of recording unit with one relationship
        RecordingUnitSummaryDTO summary1 = new RecordingUnitSummaryDTO(); summary1.setId(1L); summary1.setFullIdentifier("1");
        RecordingUnitSummaryDTO summary2 = new RecordingUnitSummaryDTO(); summary2.setId(2L); summary2.setFullIdentifier("2");
        StratigraphicRelationshipDTO relDto = new StratigraphicRelationshipDTO(); relDto.setUnit1(summary1); relDto.setUnit2(summary2);
        RecordingUnitDTO toInsertDto = new RecordingUnitDTO(); toInsertDto.setId(1L); toInsertDto.setFullIdentifier("1");
        toInsertDto.setRelationshipsAsUnit1(new HashSet<>());
        toInsertDto.getRelationshipsAsUnit1().add(relDto);

        // Entity to insert
        RecordingUnit toInsert = new RecordingUnit(); toInsert.setId(1L); toInsert.setFullIdentifier("1");
        RecordingUnit managed = new RecordingUnit(); toInsert.setId(1L); toInsert.setFullIdentifier("1");
        RecordingUnit unit2 = new RecordingUnit(); unit2.setId(2L); unit2.setFullIdentifier("2");
        StratigraphicRelationship rel = new StratigraphicRelationship(); rel.setUnit1(toInsert); rel.setUnit2(unit2);
        Set<StratigraphicRelationship> rels = new HashSet<>();
        rels.add(rel);
        toInsert.setRelationshipsAsUnit1(rels);

        when(conversionService.convert(any(RecordingUnitDTO.class), eq(RecordingUnit.class))).thenReturn(toInsert);
        when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(unit2));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversionService.convert(any(RecordingUnit.class), eq(RecordingUnitDTO.class))).thenReturn(toInsertDto);

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
        RecordingUnitSummaryDTO summary1 = new RecordingUnitSummaryDTO(); summary1.setId(1L); summary1.setFullIdentifier("1");
        RecordingUnitSummaryDTO summary2 = new RecordingUnitSummaryDTO(); summary2.setId(2L); summary2.setFullIdentifier("2");
        StratigraphicRelationshipDTO relDto = new StratigraphicRelationshipDTO(); relDto.setUnit1(summary2); relDto.setUnit2(summary1);
        RecordingUnitDTO toInsertDto = new RecordingUnitDTO(); toInsertDto.setId(1L); toInsertDto.setFullIdentifier("1");
        toInsertDto.setRelationshipsAsUnit2(new HashSet<>());
        toInsertDto.getRelationshipsAsUnit2().add(relDto);

        // Entity to insert
        RecordingUnit toInsert = new RecordingUnit(); toInsert.setId(1L); toInsert.setFullIdentifier("1");
        RecordingUnit managed = new RecordingUnit(); toInsert.setId(1L); toInsert.setFullIdentifier("1");
        RecordingUnit unit2 = new RecordingUnit(); unit2.setId(2L); unit2.setFullIdentifier("2");
        StratigraphicRelationship rel = new StratigraphicRelationship(); rel.setUnit1(unit2); rel.setUnit2(toInsert);
        Set<StratigraphicRelationship> rels = new HashSet<>();
        rels.add(rel);
        toInsert.setRelationshipsAsUnit2(rels);

        when(conversionService.convert(any(RecordingUnitDTO.class), eq(RecordingUnit.class))).thenReturn(toInsert);
        when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managed));
        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(unit2));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversionService.convert(any(RecordingUnit.class), eq(RecordingUnitDTO.class))).thenReturn(toInsertDto);

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
    void save_UpdatesExistingStratigraphicRelationshipsAsUnit1() {
        // Arrange
        RecordingUnitDTO recordingUnit = new RecordingUnitDTO();
        RecordingUnitDTO managedRecordingUnit = new RecordingUnitDTO();
        managedRecordingUnit.setId(1L);
        RecordingUnitSummaryDTO r1Sum = new RecordingUnitSummaryDTO();
        r1Sum.setId(1L);
        RecordingUnit managedRecordingUnit2 = new RecordingUnit();
        managedRecordingUnit.setId(2L);
        RecordingUnitSummaryDTO recordingUnitSummaryDTO1 = new RecordingUnitSummaryDTO();
        recordingUnitSummaryDTO1.setId(1L);

        // Setup existing relationships for managedRecordingUnit
        Set<StratigraphicRelationshipDTO> existingRelationshipsAsUnit1 = new HashSet<>();
        StratigraphicRelationshipDTO existingRel = new StratigraphicRelationshipDTO();
        RecordingUnitSummaryDTO unit2 = new RecordingUnitSummaryDTO();
        unit2.setId(2L);
        existingRel.setUnit1(recordingUnitSummaryDTO1);
        existingRel.setUnit2(unit2);
        existingRel.setConcept(new ConceptDTO());
        existingRelationshipsAsUnit1.add(existingRel);
        managedRecordingUnit.setRelationshipsAsUnit1(existingRelationshipsAsUnit1);

        // Setup relationships for recordingUnit with updated concept
        Set<StratigraphicRelationshipDTO> relationshipsAsUnit1 = new HashSet<>();
        StratigraphicRelationshipDTO updatedRel = new StratigraphicRelationshipDTO();
        updatedRel.setUnit1(recordingUnitSummaryDTO1);
        updatedRel.setUnit2(unit2);
        ConceptDTO newConcept = new ConceptDTO();
        newConcept.setId(100L);
        updatedRel.setConcept(newConcept);
        relationshipsAsUnit1.add(updatedRel);
        recordingUnit.setRelationshipsAsUnit1(relationshipsAsUnit1);

        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(managedRecordingUnit2));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conceptService.saveOrGetConcept(any(Concept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RecordingUnitDTO result = recordingUnitService.save(recordingUnit);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getRelationshipsAsUnit1().size());
        StratigraphicRelationshipDTO savedRel = result.getRelationshipsAsUnit1().iterator().next();
        assertEquals(unit2, savedRel.getUnit2());
        assertEquals(r1Sum, savedRel.getUnit1());
        assertEquals(newConcept, savedRel.getConcept());
    }

    @Test
    void save_UpdatesExistingStratigraphicRelationshipsAsUnit2() {
        // Arrange
        RecordingUnitDTO recordingUnit = new RecordingUnitDTO();
        recordingUnit.setId(2L); // The unit we are saving (Unit 2)

        RecordingUnitDTO managedRecordingUnit = new RecordingUnitDTO();
        managedRecordingUnit.setId(2L);

        RecordingUnitSummaryDTO r2Sum = new RecordingUnitSummaryDTO();
        r2Sum.setId(2L); // Summary for the unit being saved

        RecordingUnit managedRecordingUnit1 = new RecordingUnit();
        managedRecordingUnit1.setId(1L); // The "other" side of the link (Unit 1)

        RecordingUnitSummaryDTO unit1Summary = new RecordingUnitSummaryDTO();
        unit1Summary.setId(1L);

        // 1. Setup existing relationship where managedRecordingUnit is Unit 2
        Set<StratigraphicRelationshipDTO> existingRelationshipsAsUnit2 = new HashSet<>();
        StratigraphicRelationshipDTO existingRel = new StratigraphicRelationshipDTO();
        existingRel.setUnit1(unit1Summary);
        existingRel.setUnit2(r2Sum);
        existingRel.setConcept(new ConceptDTO());
        existingRelationshipsAsUnit2.add(existingRel);
        managedRecordingUnit.setRelationshipsAsUnit2(existingRelationshipsAsUnit2);

        // 2. Setup incoming DTO with updated concept
        Set<StratigraphicRelationshipDTO> relationshipsAsUnit2 = new HashSet<>();
        StratigraphicRelationshipDTO updatedRel = new StratigraphicRelationshipDTO();
        updatedRel.setUnit1(unit1Summary);
        updatedRel.setUnit2(r2Sum);

        ConceptDTO newConcept = new ConceptDTO();
        newConcept.setId(100L);
        updatedRel.setConcept(newConcept);

        relationshipsAsUnit2.add(updatedRel);
        recordingUnit.setRelationshipsAsUnit2(relationshipsAsUnit2);

        // Mocks
        when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managedRecordingUnit1));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conceptService.saveOrGetConcept(any(Concept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RecordingUnitDTO result = recordingUnitService.save(recordingUnit);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getRelationshipsAsUnit2().size(), "Should have 1 relationship as Unit 2");

        StratigraphicRelationshipDTO savedRel = result.getRelationshipsAsUnit2().iterator().next();
        assertEquals(unit1Summary, savedRel.getUnit1(), "Unit 1 should match the existing link");
        assertEquals(r2Sum, savedRel.getUnit2(), "Unit 2 should be the saved unit");
        assertEquals(newConcept.getId(), savedRel.getConcept().getId(), "Concept should be updated to 100L");
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
}