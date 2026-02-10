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
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdCounterRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdInfoRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
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

        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenReturn(recordingUnit);

        ArkEntity result = recordingUnitService.save(recordingUnit);

        assertNotNull(result);
        assertEquals(recordingUnit, result);
    }

    @Test
    void countByInstitution_success() {
        when(recordingUnitRepository.countByCreatedByInstitution(any(Institution.class))).thenReturn(3L);
        assertEquals(3,recordingUnitService.countByInstitution(new Institution()));
    }

    @Test
    void save_SyncsStratigraphicRelationshipsAsUnit1() {
        // Arrange
        RecordingUnit recordingUnit = new RecordingUnit();
        RecordingUnit managedRecordingUnit = new RecordingUnit();
        managedRecordingUnit.setId(1L);

        // Setup relationships for recordingUnit
        Set<StratigraphicRelationship> relationshipsAsUnit1 = new HashSet<>();
        StratigraphicRelationship relUnit1 = new StratigraphicRelationship();
        RecordingUnit unit2 = new RecordingUnit();
        unit2.setId(2L);
        relUnit1.setUnit1(recordingUnit);
        relUnit1.setUnit2(unit2);
        relUnit1.setConcept(new Concept());
        relationshipsAsUnit1.add(relUnit1);
        recordingUnit.setRelationshipsAsUnit1(relationshipsAsUnit1);

        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(unit2));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conceptService.saveOrGetConcept(any(Concept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RecordingUnit result = recordingUnitService.save(recordingUnit, new Concept());

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getRelationshipsAsUnit1().size());
        StratigraphicRelationship savedRel = result.getRelationshipsAsUnit1().iterator().next();
        assertEquals(unit2, savedRel.getUnit2());
        assertEquals(result, savedRel.getUnit1());
    }

    @Test
    void save_SyncsStratigraphicRelationshipsAsUnit2() {
        // Arrange
        RecordingUnit recordingUnit = new RecordingUnit();
        RecordingUnit managedRecordingUnit = new RecordingUnit();
        managedRecordingUnit.setId(1L);

        // Setup relationships for recordingUnit
        Set<StratigraphicRelationship> relationshipsAsUnit2 = new HashSet<>();
        StratigraphicRelationship relUnit2 = new StratigraphicRelationship();
        RecordingUnit unit1 = new RecordingUnit();
        unit1.setId(2L);
        relUnit2.setUnit1(unit1);
        relUnit2.setUnit2(recordingUnit);
        relUnit2.setConcept(new Concept());
        relationshipsAsUnit2.add(relUnit2);
        recordingUnit.setRelationshipsAsUnit2(relationshipsAsUnit2);

        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(unit1));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conceptService.saveOrGetConcept(any(Concept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RecordingUnit result = recordingUnitService.save(recordingUnit, new Concept());

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getRelationshipsAsUnit2().size());
        StratigraphicRelationship savedRel = result.getRelationshipsAsUnit2().iterator().next();
        assertEquals(unit1, savedRel.getUnit1());
        assertEquals(result, savedRel.getUnit2());
    }

    @Test
    void save_UpdatesExistingStratigraphicRelationshipsAsUnit1() {
        // Arrange
        RecordingUnit recordingUnit = new RecordingUnit();
        RecordingUnit managedRecordingUnit = new RecordingUnit();
        managedRecordingUnit.setId(1L);

        // Setup existing relationships for managedRecordingUnit
        Set<StratigraphicRelationship> existingRelationshipsAsUnit1 = new HashSet<>();
        StratigraphicRelationship existingRel = new StratigraphicRelationship();
        RecordingUnit unit2 = new RecordingUnit();
        unit2.setId(2L);
        existingRel.setUnit1(managedRecordingUnit);
        existingRel.setUnit2(unit2);
        existingRel.setConcept(new Concept());
        existingRelationshipsAsUnit1.add(existingRel);
        managedRecordingUnit.setRelationshipsAsUnit1(existingRelationshipsAsUnit1);

        // Setup relationships for recordingUnit with updated concept
        Set<StratigraphicRelationship> relationshipsAsUnit1 = new HashSet<>();
        StratigraphicRelationship updatedRel = new StratigraphicRelationship();
        updatedRel.setUnit1(recordingUnit);
        updatedRel.setUnit2(unit2);
        Concept newConcept = new Concept();
        newConcept.setId(100L);
        updatedRel.setConcept(newConcept);
        relationshipsAsUnit1.add(updatedRel);
        recordingUnit.setRelationshipsAsUnit1(relationshipsAsUnit1);

        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(unit2));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conceptService.saveOrGetConcept(any(Concept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RecordingUnit result = recordingUnitService.save(recordingUnit, new Concept());

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getRelationshipsAsUnit1().size());
        StratigraphicRelationship savedRel = result.getRelationshipsAsUnit1().iterator().next();
        assertEquals(unit2, savedRel.getUnit2());
        assertEquals(result, savedRel.getUnit1());
        assertEquals(newConcept, savedRel.getConcept());
    }

    @Test
    void save_UpdatesExistingStratigraphicRelationshipsAsUnit2() {
        // Arrange
        RecordingUnit recordingUnit = new RecordingUnit();
        RecordingUnit managedRecordingUnit = new RecordingUnit();
        managedRecordingUnit.setId(1L);

        // Setup existing relationships for managedRecordingUnit
        Set<StratigraphicRelationship> existingRelationshipsAsUnit2 = new HashSet<>();
        StratigraphicRelationship existingRel = new StratigraphicRelationship();
        RecordingUnit unit1 = new RecordingUnit();
        unit1.setId(2L);
        existingRel.setUnit1(unit1);
        existingRel.setUnit2(managedRecordingUnit);
        existingRel.setConcept(new Concept());
        existingRelationshipsAsUnit2.add(existingRel);
        managedRecordingUnit.setRelationshipsAsUnit2(existingRelationshipsAsUnit2);

        // Setup relationships for recordingUnit with updated concept
        Set<StratigraphicRelationship> relationshipsAsUnit2 = new HashSet<>();
        StratigraphicRelationship updatedRel = new StratigraphicRelationship();
        updatedRel.setUnit1(unit1);
        updatedRel.setUnit2(recordingUnit);
        Concept newConcept = new Concept();
        newConcept.setId(100L);
        updatedRel.setConcept(newConcept);
        relationshipsAsUnit2.add(updatedRel);
        recordingUnit.setRelationshipsAsUnit2(relationshipsAsUnit2);

        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(unit1));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conceptService.saveOrGetConcept(any(Concept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RecordingUnit result = recordingUnitService.save(recordingUnit, new Concept());

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getRelationshipsAsUnit2().size());
        StratigraphicRelationship savedRel = result.getRelationshipsAsUnit2().iterator().next();
        assertEquals(unit1, savedRel.getUnit1());
        assertEquals(result, savedRel.getUnit2());
        assertEquals(newConcept, savedRel.getConcept());
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