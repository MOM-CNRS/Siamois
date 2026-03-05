package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.mapper.RecordingUnitMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private RecordingUnitMapper recordingUnitMapper;

    @Mock
    private PersonRepository personRepository;


    @InjectMocks
    private RecordingUnitService recordingUnitService;

    @BeforeEach
    void setUp() {

    }


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
}