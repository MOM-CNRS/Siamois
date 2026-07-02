package fr.siamois.domain.services;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.CustomFormResponseService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuIdentifierResolver;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuNumericalIdentifierResolver;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.api.dto.ConceptFieldDTO;
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
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import fr.siamois.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;


@ExtendWith(MockitoExtension.class)
class RecordingUnitServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);


    @Mock
    private RecordingUnitRepository recordingUnitRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private CustomFormResponseService customFormResponseService;
    @Mock
    private ConceptService conceptService;
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
    private ApplicationContext applicationContext;
    @Mock
    private RecordingUnitMapper recordingUnitMapper;
    @Mock
    private ActionUnitSummaryMapper actionUnitSummaryMapper;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private CustomFormResponseRepository customFormResponseRepository;
    @Mock
    private ArkRepository arkRepository;
    @Mock
    private StratigraphicRelationshipRepository stratigraphicRelationshipRepository;
    @Mock
    private StatigraphicRelationshipMapper stratigraphicRelationshipMapper;
    @Mock
    private RecordingUnitSummaryMapper recordingUnitSummaryMapper;
    @Mock
    private ConversionService conversionService;
    @Mock
    private PhaseRepository phaseRepository;
    @Mock
    private PhaseMapper phaseMapper;


    @InjectMocks
    private RecordingUnitService recordingUnitService;

    SpatialUnit spatialUnit1;
    RecordingUnit recordingUnit1;
    RecordingUnitDTO recordingUnit1DTO;
    RecordingUnit recordingUnit2;
    RecordingUnitDTO recordingUnit2DTO;
    Ark newArk;
    Vocabulary vocabulary;
    ConceptFieldDTO dto;
    Concept concept;


    RecordingUnitDTO recordingUnitToSave;

    Page<RecordingUnit> page ;
    Page<RecordingUnitDTO> pageDto ;
    Pageable pageable;

    // For testing permission related method
    private PersonDTO user;
    private UserInfo userInfo;

    private ActionUnitSummaryDTO actionUnit;
    private ActionUnitSummaryDTO a1;

    @BeforeEach
    void setUp() {

        InstitutionDTO institutionDTO = new InstitutionDTO(); institutionDTO.setId(1L);

        a1 = new ActionUnitSummaryDTO(); a1.setId(1L);
        a1.setCreatedByInstitution(institutionDTO);

        spatialUnit1 = new SpatialUnit();
        recordingUnit1 = new RecordingUnit();
        recordingUnit1DTO = new RecordingUnitDTO();
        recordingUnit2DTO = new RecordingUnitDTO();
        recordingUnit2DTO.setActionUnit(a1);
        recordingUnit2 = new RecordingUnit();
        spatialUnit1.setId(1L);
        recordingUnit1.setId(1L);
        recordingUnit1DTO.setActionUnit(a1);
        recordingUnit1DTO.setId(1L);
        recordingUnit1DTO.setId(2L);
        recordingUnit2.setId(2L);

        concept = new Concept();
        newArk = new Ark();
        vocabulary = new Vocabulary();
        dto = new ConceptFieldDTO();

        Institution parentInstitution = new Institution();
        parentInstitution.setIdentifier("MOM");
        InstitutionDTO parentInstitutionDto = new InstitutionDTO();
        parentInstitutionDto.setIdentifier("MOM");
        actionUnit = new ActionUnitSummaryDTO();
        actionUnit.setIdentifier("2025");
        actionUnit.setMinRecordingUnitCode(5);
        actionUnit.setId(1L);
        actionUnit.setMaxRecordingUnitCode(5);
        actionUnit.setCreatedByInstitution(parentInstitutionDto);
        recordingUnitToSave = new RecordingUnitDTO();
        recordingUnitToSave.setActionUnit(actionUnit);
        recordingUnitToSave.setCreatedByInstitution(parentInstitutionDto);
        recordingUnitToSave.setParents(new HashSet<>());
        recordingUnitToSave.setChildren(new HashSet<>());

        page = new PageImpl<>(List.of(recordingUnit1, recordingUnit2));
        pageDto = new PageImpl<>(List.of(recordingUnit1DTO, recordingUnit2DTO));
        pageable = PageRequest.of(0, 10);


        // Permission related methods
        user = new PersonDTO();
        userInfo = new UserInfo(parentInstitutionDto, user, "fr");

        // setupParents appelle findAllById même pour une liste vide ; sans stub, Mockito renvoie null → NPE.
        lenient().when(recordingUnitRepository.findAllById(argThat(list -> list == null || !list.iterator().hasNext())))
                .thenReturn(Collections.emptyList());
    }

    @Test
    void findById_success() {
        // Arrange
        Long id = 1L;
        RecordingUnit recordingUnit = new RecordingUnit();
        recordingUnit.setId(id);

        RecordingUnitDTO expectedDTO = new RecordingUnitDTO();
        expectedDTO.setId(id);

        when(recordingUnitRepository.findById(id)).thenReturn(Optional.of(recordingUnit));
        when(recordingUnitMapper.convert(recordingUnit)).thenReturn(expectedDTO);

        // Act
        RecordingUnitDTO actualResult = recordingUnitService.findById(id);

        // Assert
        assertEquals(expectedDTO, actualResult);
        verify(recordingUnitRepository).findById(id);
        verify(recordingUnitMapper).convert(recordingUnit);
    }


    @Test
    void findById_Exception() {

        when(recordingUnitRepository.findById(recordingUnit1.getId())).thenReturn(Optional.empty());


        // Act & Assert
        Exception exception = assertThrows(
                Exception.class,
                () -> recordingUnitService.findById(recordingUnit1.getId())
        );

        assertEquals("RecordingUnit not found with ID: 1", exception.getMessage());

    }

    @Test
    void bulkUpdateType_shouldCallRepository() {
        // Arrange
        List<Long> ids = List.of(1L, 2L, 3L);
        ConceptDTO newType = new ConceptDTO();
        newType.setId(10L);
        when(recordingUnitRepository.updateTypeByIds(newType.getId(), ids)).thenReturn(ids.size());

        // Act
        int updatedCount = recordingUnitService.bulkUpdateType(ids, newType);

        // Assert
        assertEquals(ids.size(), updatedCount);
        verify(recordingUnitRepository).updateTypeByIds(newType.getId(), ids);
    }

    @Test
    void save_Success() {
        // --- 1. Setup Entités JPA (Managées et liées) ---
        RecordingUnit managedUnit = new RecordingUnit();
        managedUnit.setId(1L);
        managedUnit.setParents(new HashSet<>());
        managedUnit.setChildren(new HashSet<>());
        managedUnit.setRelationshipsAsUnit1(new HashSet<>());
        managedUnit.setRelationshipsAsUnit2(new HashSet<>());
        managedUnit.setContributors(new ArrayList<>());

        RecordingUnit parentEntity = new RecordingUnit();
        parentEntity.setId(10L);
        parentEntity.setChildren(new HashSet<>());

        // Cibles pour les relations stratigraphiques
        RecordingUnit targetUnit2 = new RecordingUnit(); targetUnit2.setId(2L);
        RecordingUnit targetUnit3 = new RecordingUnit(); targetUnit3.setId(3L);

        // --- 2. Setup Entité issue du Mapper ---
        RecordingUnit entityFromMapper = new RecordingUnit();
        entityFromMapper.setId(1L);
        entityFromMapper.setParents(new HashSet<>(Set.of(parentEntity)));
        entityFromMapper.setChildren(new HashSet<>());
        entityFromMapper.setContributors(new ArrayList<>(List.of(new Person())));

        // Correction de la NPE : On initialise les objets Unit1/Unit2 dans les relations
        StratigraphicRelationship relAsUnit1 = new StratigraphicRelationship();
        relAsUnit1.setUnit1(entityFromMapper);
        relAsUnit1.setUnit2(targetUnit2); // Ne doit pas être null !
        relAsUnit1.setConcept(new Concept());
        entityFromMapper.setRelationshipsAsUnit1(new HashSet<>(Set.of(relAsUnit1)));

        StratigraphicRelationship relAsUnit2 = new StratigraphicRelationship();
        relAsUnit2.setUnit2(entityFromMapper);
        relAsUnit2.setUnit1(targetUnit3); // Ne doit pas être null !
        relAsUnit2.setConcept(new Concept());
        entityFromMapper.setRelationshipsAsUnit2(new HashSet<>(Set.of(relAsUnit2)));

        // --- 3. Mocking ---
        when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(entityFromMapper);

        // Le service cherche l'unité existante car l'ID est 1L
        when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(managedUnit));
        when(recordingUnitRepository.findById(2L)).thenReturn(Optional.of(targetUnit2));
        when(recordingUnitRepository.findById(3L)).thenReturn(Optional.of(targetUnit3));
        when(recordingUnitRepository.findAllById(argThat(ids -> {
            List<Long> collected = new ArrayList<>();
            ids.forEach(collected::add);
            return collected.equals(List.of(10L));
        }))).thenReturn(List.of(parentEntity));
        when(recordingUnitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(personRepository.findAllById(anyList())).thenReturn(List.of(new Person()));

        // Mock du retour final
        when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(recordingUnitToSave);

        // --- 4. Execute ---
        RecordingUnitDTO result = recordingUnitService.save(recordingUnitToSave);

        // --- 5. Assertions ---
        assertNotNull(result);
        verify(recordingUnitRepository, atLeastOnce()).save(any(RecordingUnit.class));
        verify(recordingUnitRepository).findAllById(argThat(ids -> {
            List<Long> collected = new ArrayList<>();
            ids.forEach(collected::add);
            return collected.equals(List.of(10L));
        }));
        verify(recordingUnitRepository).findById(2L);
        verify(recordingUnitRepository).findById(3L);
    }



    @Test
    void save_shouldUpdateExistingUnit_whenIdIsProvided() {
        // Arrange
        long existingId = 42L;
        RecordingUnitDTO recordingUnitToSave2 = new RecordingUnitDTO();
        recordingUnitToSave2.setId(existingId);
        recordingUnitToSave2.setDescription("Updated description");

        RecordingUnit foundUnit = new RecordingUnit();
        foundUnit.setId(existingId);
        foundUnit.setDescription("Original description");

        RecordingUnit savedUnit = new RecordingUnit();
        savedUnit.setId(existingId);
        savedUnit.setDescription("Updated description");

        RecordingUnitDTO expectedDTO = new RecordingUnitDTO();
        expectedDTO.setId(existingId);
        expectedDTO.setDescription("Updated description");

        // Mock the mapper to return a RecordingUnit when converting from DTO
        when(recordingUnitMapper.invertConvert(recordingUnitToSave2)).thenReturn(savedUnit);
        // Mock the repository to return the found unit when searching by ID
        when(recordingUnitRepository.findById(existingId)).thenReturn(Optional.of(foundUnit));
        // Mock the repository to return the saved unit when saving
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenReturn(savedUnit);
        // Mock the mapper to return the expected DTO when converting back
        when(recordingUnitMapper.convert(savedUnit)).thenReturn(expectedDTO);
        // Mock the person repository to return an empty list
        when(personRepository.findAllById(anyList())).thenReturn(List.of());

        // Act
        RecordingUnitDTO result = recordingUnitService.save(recordingUnitToSave2);

        // Assert
        assertNotNull(result);
        assertEquals(existingId, result.getId());
        assertEquals("Updated description", result.getDescription());
        verify(recordingUnitRepository).findById(existingId);
        // save() ne persiste plus que l'entité managée une seule fois (plus de second save)
        verify(recordingUnitRepository).save(foundUnit);
    }


    @Test
    void save_shouldCreateNewUnit_whenIdIsProvidedButNotFound() {
        // Arrange
        long nonExistentId = 43L;
        RecordingUnitDTO recordingUnitToSave2 = new RecordingUnitDTO();
        recordingUnitToSave2.setId(nonExistentId);
        recordingUnitToSave2.setDescription("New unit with given ID");

        RecordingUnit newUnit = new RecordingUnit();
        newUnit.setId(99L); // Simulate a new ID assigned by the repository
        newUnit.setDescription("New unit with given ID");

        RecordingUnitDTO expectedDTO = new RecordingUnitDTO();
        expectedDTO.setId(99L);
        expectedDTO.setDescription("New unit with given ID");

        // Mock the mapper to return a RecordingUnit when converting from DTO
        when(recordingUnitMapper.invertConvert(recordingUnitToSave2)).thenReturn(newUnit);
        // Mock the repository to return empty for any ID (simulating "not found")
        when(recordingUnitRepository.findById(anyLong())).thenReturn(Optional.empty());
        // Mock the repository to return the new unit when saving
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenReturn(newUnit);
        // Mock the mapper to return the expected DTO when converting back
        when(recordingUnitMapper.convert(newUnit)).thenReturn(expectedDTO);
        // Mock the person repository to return an empty list
        when(personRepository.findAllById(anyList())).thenReturn(List.of());

        // Act
        RecordingUnitDTO result = recordingUnitService.save(recordingUnitToSave2);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(99L, result.getId()); // Verify the new ID is set
        assertEquals("New unit with given ID", result.getDescription());
        verify(recordingUnitRepository).findById(99L);
        // L'entité managée créée en interne est persistée une seule fois
        verify(recordingUnitRepository, times(1)).save(any(RecordingUnit.class));
    }



    @Test
    void save_shouldThrowFailedRecordingUnitSaveException_whenDependencyFails() {
        // Arrange
        RecordingUnitDTO recordingUnitToSave2 = new RecordingUnitDTO();
        RecordingUnit recordingUnit = new RecordingUnit();

        // Mock the mapper to return a RecordingUnit when converting from DTO
        when(recordingUnitMapper.invertConvert(recordingUnitToSave2)).thenReturn(recordingUnit);

        // Mock the repository save to avoid NullPointerException
        when(recordingUnitRepository.save(any(RecordingUnit.class)))
                .thenThrow(new RuntimeException("Dependency error"));

        // Act & Assert
        FailedRecordingUnitSaveException exception = assertThrows(
                FailedRecordingUnitSaveException.class,
                () -> recordingUnitService.save(recordingUnitToSave2)
        );

        // Verify the exception message contains the expected error
        assertTrue(exception.getMessage().contains("Dependency error"));
    }




    @Test
    void canCreateSpecimen_returnsTrue_whenUserIsManagerOfCreatingInstitution() {


        when(institutionService.isManagerOf(any(InstitutionDTO.class), any(PersonDTO.class))).thenReturn(true);

        boolean result = recordingUnitService.canCreateSpecimen(userInfo, recordingUnit1DTO);

        assertTrue(result);
    }

    @Test
    void canCreateSpecimen_returnsTrue_whenUserIsActionUnitManager() {


        when(institutionService.isManagerOf(any(InstitutionDTO.class), any(PersonDTO.class))).thenReturn(false);
        when(actionUnitService.isManagerOf(a1, user)).thenReturn(true);

        boolean result = recordingUnitService.canCreateSpecimen(userInfo, recordingUnit2DTO);

        assertTrue(result);
    }

    @Test
    void canCreateSpecimen_returnsTrue_whenUserIsTeamMember_andActionUnitIsOngoing() {



        when(institutionService.isManagerOf(any(InstitutionDTO.class), any(PersonDTO.class))).thenReturn(false);
        when(actionUnitService.isManagerOf(actionUnit, user)).thenReturn(false);
        when(teamMemberRepository.existsByActionUnitIdAndPerson(anyLong(), any(PersonDTO.class))).thenReturn(true);
        when(actionUnitService.isActionUnitStillOngoing(actionUnit)).thenReturn(true);

        boolean result = recordingUnitService.canCreateSpecimen(userInfo, recordingUnitToSave);

        assertTrue(result);
    }

    @Test
    void canCreateSpecimen_returnsFalse_whenUserIsTeamMember_butActionUnitIsNotOngoing() {


        when(institutionService.isManagerOf(any(InstitutionDTO.class), any(PersonDTO.class))).thenReturn(false);
        when(actionUnitService.isManagerOf(actionUnit, user)).thenReturn(false);
        when(teamMemberRepository.existsByActionUnitIdAndPerson(anyLong(), any(PersonDTO.class))).thenReturn(true);
        when(actionUnitService.isActionUnitStillOngoing(actionUnit)).thenReturn(false);

        boolean result = recordingUnitService.canCreateSpecimen(userInfo, recordingUnitToSave);

        assertFalse(result);
    }

    @Test
    void canCreateSpecimen_returnsFalse_whenUserHasNoPermissions() {


        when(institutionService.isManagerOf(any(InstitutionDTO.class), any(PersonDTO.class))).thenReturn(false);
        when(actionUnitService.isManagerOf(actionUnit, user)).thenReturn(false);
        when(teamMemberRepository.existsByActionUnitIdAndPerson(anyLong(), any(PersonDTO.class))).thenReturn(false);

        boolean result = recordingUnitService.canCreateSpecimen(userInfo, recordingUnitToSave);

        assertFalse(result);
        verify(actionUnitService, never()).isActionUnitStillOngoing(any());
    }



    @Test
    void testCountBySpatialContext() {
        // Arrange
        SpatialUnit spatialUnit = new SpatialUnit();
        spatialUnit.setId(1L);
        when(recordingUnitRepository.countBySpatialContext(1L)).thenReturn(5);

        // Act
        Integer count = recordingUnitService.countBySpatialContext(spatialUnit);

        // Assert
        assertEquals(5, count);
        verify(recordingUnitRepository, times(1)).countBySpatialContext(1L);
    }

    @Test
    void testCountByActionContext() {
        // Arrange
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(2L);
        when(recordingUnitRepository.countByActionContext(2L)).thenReturn(3);

        // Act
        Integer count = recordingUnitService.countByActionContext(au);

        // Assert
        assertEquals(3, count);
        verify(recordingUnitRepository, times(1)).countByActionContext(2L);
    }

    @Test
    void findAllWithoutParentsByInstitution_returnsInitializedRecordingUnits() {
        // Arrange
        Long institutionId = 1L;
        List<RecordingUnit> mockRecordingUnits = List.of(mock(RecordingUnit.class), mock(RecordingUnit.class));
        when(recordingUnitRepository.findRootsByInstitution(institutionId)).thenReturn(mockRecordingUnits);

        // Act
        List<RecordingUnitDTO> result = recordingUnitService.findAllWithoutParentsByInstitution(institutionId);

        // Assert
        assertEquals(2, result.size());
        verify(recordingUnitRepository).findRootsByInstitution(institutionId);
    }

    @Test
    void findChildrenByParentAndInstitution_returnsInitializedRecordingUnits() {
        // Arrange
        Long parentId = 1L;
        Long institutionId = 1L;
        List<RecordingUnit> mockRecordingUnits = List.of(mock(RecordingUnit.class), mock(RecordingUnit.class));
        when(recordingUnitRepository.findChildrenByParentAndInstitution(parentId, institutionId)).thenReturn(mockRecordingUnits);

        // Act
        List<RecordingUnitDTO> result = recordingUnitService.findChildrenByParentAndInstitution(
                parentId, institutionId);

        // Assert
        assertEquals(2, result.size());
        verify(recordingUnitRepository).findChildrenByParentAndInstitution(parentId, institutionId);
    }

    @Test
    void findAllWithoutParentsByAction_returnsInitializedRecordingUnits() {
        // Arrange
        Long actionId = 1L;
        List<RecordingUnit> mockRecordingUnits = List.of(mock(RecordingUnit.class), mock(RecordingUnit.class));
        when(recordingUnitRepository.findRootsByAction(actionId)).thenReturn(mockRecordingUnits);

        // Act
        List<RecordingUnitDTO> result = recordingUnitService.findAllWithoutParentsByAction(actionId);

        // Assert
        assertEquals(2, result.size());
        verify(recordingUnitRepository).findRootsByAction(actionId);
    }

    @Test
    void fullIdentifierAlreadyExistInAction_returnTrue_whenSameIdentifierIsNotSameUnit() {
        recordingUnit1DTO = new RecordingUnitDTO();
        recordingUnit1DTO.setId(1L);
        recordingUnit1DTO.setActionUnit(a1);
        recordingUnit1DTO.setFullIdentifier("test");
        recordingUnit2.setFullIdentifier("test");

        when(recordingUnitRepository.findByFullIdentifierAndActionUnitId("test", 1L))
                .thenReturn(List.of(recordingUnit1, recordingUnit2));

        assertTrue(recordingUnitService.fullIdentifierAlreadyExistInAction(recordingUnit1DTO));
    }

    @Test
    void fullIdentifierAlreadyExistInAction_returnFalse_whenSameIdentifierIsSameUnit() {
        recordingUnit1DTO = new RecordingUnitDTO();
        recordingUnit1DTO.setId(1L);
        ActionUnitSummaryDTO act = new ActionUnitSummaryDTO(); act.setId(1L);
        recordingUnit1DTO.setActionUnit(act);
        recordingUnit1DTO.setFullIdentifier("test");

        when(recordingUnitRepository.findByFullIdentifierAndActionUnitId("test", 1L))
                .thenReturn(List.of(recordingUnit1, recordingUnit1));

        assertFalse(recordingUnitService.fullIdentifierAlreadyExistInAction(recordingUnit1DTO));
    }

    @Test
    void fullIdentifierAlreadyExistInAction_returnFalse_whenIdentifierDoesntAlreadyExist() {
        // Arrange
        RecordingUnitDTO recordingUnit1DTO2 = new RecordingUnitDTO();
        recordingUnit1DTO2.setId(1L);

        ActionUnitSummaryDTO actionUnitSummaryDTO = new ActionUnitSummaryDTO();
        actionUnitSummaryDTO.setId(1L);

        recordingUnit1DTO2.setActionUnit(actionUnitSummaryDTO);
        recordingUnit1DTO2.setFullIdentifier("test");

        // Mock the repository to return an empty list
        when(recordingUnitRepository.findByFullIdentifierAndActionUnitId("test", 1L))
                .thenReturn(Collections.emptyList());

        // Act
        boolean exists = recordingUnitService.fullIdentifierAlreadyExistInAction(recordingUnit1DTO2);

        // Assert
        assertFalse(exists, "The identifier should not exist in the action");
    }


    @Test
    void findWithoutArk_returnsListOfRecordingUnits() {
        // Arrange
        Institution institution = new Institution();
        institution.setId(1L);
        List<RecordingUnit> mockRecordingUnits = List.of(recordingUnit1, recordingUnit2);
        when(recordingUnitRepository.findAllWithoutArkOfInstitution(institution.getId())).thenReturn(mockRecordingUnits);

        // Act
        List<? extends ArkEntity> result = recordingUnitService.findWithoutArk(institution);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(recordingUnit1, result.get(0));
        assertEquals(recordingUnit2, result.get(1));
        verify(recordingUnitRepository, times(1)).findAllWithoutArkOfInstitution(institution.getId());
    }



    @Test
    void countByInstitution_returnsCorrectCount() {
        // Arrange
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        when(recordingUnitRepository.countByCreatedByInstitutionId(1L)).thenReturn(10L);

        // Act
        long count = recordingUnitService.countByInstitutionId(1L);

        // Assert
        assertEquals(10L, count);
        verify(recordingUnitRepository, times(1)).countByCreatedByInstitutionId(any(Long.class));
    }

    @Test
    void generatedNextIdentifier_UNIQUE_returnsUniqueId() {
        // Arrange
        ActionUnit actionUnit2 = new ActionUnit();
        actionUnit2.setId(1L);
        actionUnit2.setRecordingUnitIdentifierFormat("{NUM_UE}"); // This will make resolveConfig() return UNIQUE

        when(recordingUnitIdCounterRepository.ruNextValUnique(1L)).thenReturn(100);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(actionUnit2, null, null);

        // Assert
        assertEquals(100, result, "The generated identifier should be 100");
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValUnique(1L);
    }



    @Test
    void generatedNextIdentifier_PARENT_withNullParent_returnsUniqueId() {
        // Arrange
        ActionUnit action = new ActionUnit(); action.setId(1L);
        action.setRecordingUnitIdentifierFormat("{NUM_PARENT}-{NUM_UE}");
        when(recordingUnitIdCounterRepository.ruNextValUnique(action.getId())).thenReturn(101);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(action, null, null);

        // Assert
        assertEquals(101, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValUnique(actionUnit.getId());
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }

    @Test
    void generatedNextIdentifier_PARENT_withParent_returnsParentId() {
        // Arrange
        ActionUnit action = new ActionUnit(); action.setId(1L);
        action.setRecordingUnitIdentifierFormat("{NUM_PARENT}-{NUM_UE}");
        RecordingUnit parentRu = new RecordingUnit();
        parentRu.setId(5L);
        when(recordingUnitIdCounterRepository.ruNextValParent(parentRu.getId())).thenReturn(102);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(action, null, parentRu);

        // Assert
        assertEquals(102, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValParent(parentRu.getId());
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }

    @Test
    void generatedNextIdentifier_TYPE_UNIQUE_returnsTypeId() {
        // Arrange
        ActionUnit action = new ActionUnit(); action.setId(1L);
        action.setRecordingUnitIdentifierFormat("{TYPE_UE}-{NUM_UE}");
        Concept unitType = new Concept();
        unitType.setId(10L);
        when(recordingUnitIdCounterRepository.ruNextValTypeUnique(actionUnit.getId(), unitType.getId())).thenReturn(103);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(action, unitType, null);

        // Assert
        assertEquals(103, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValTypeUnique(actionUnit.getId(), unitType.getId());
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }

    @Test
    void generatedNextIdentifier_TYPE_UNIQUE_withNullType_returnsTypeId() {
        // Arrange
        ActionUnit action = new ActionUnit(); action.setId(1L);
        action.setRecordingUnitIdentifierFormat("{TYPE_UE}-{NUM_UE}");
        when(recordingUnitIdCounterRepository.ruNextValTypeUnique(actionUnit.getId(), null)).thenReturn(104);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(action, null, null);

        // Assert
        assertEquals(104, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValTypeUnique(actionUnit.getId(), null);
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }

    @Test
    void generatedNextIdentifier_PARENT_TYPE_withNullParent_returnsUniqueId() {
        // Arrange
        ActionUnit action = new ActionUnit();
        action.setRecordingUnitIdentifierFormat("{TYPE_UE}{NUM_PARENT}-{NUM_UE}");
        action.setId(1L);
        Concept unitType = new Concept();
        unitType.setId(10L);
        when(recordingUnitIdCounterRepository.ruNextValUnique(actionUnit.getId())).thenReturn(105);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(action, unitType, null);

        // Assert
        assertEquals(105, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValUnique(actionUnit.getId());
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }

    @Test
    void generatedNextIdentifier_PARENT_TYPE_withParentAndType_returnsParentTypeId() {
        // Arrange
        ActionUnit action = new ActionUnit();
        action.setId(1L);
        action.setRecordingUnitIdentifierFormat("{TYPE_UE}{NUM_PARENT}-{NUM_UE}");
        RecordingUnit parentRu = new RecordingUnit();
        parentRu.setId(5L);
        Concept unitType = new Concept();
        unitType.setId(10L);
        when(recordingUnitIdCounterRepository.ruNextValTypeParent(parentRu.getId(), unitType.getId())).thenReturn(106);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(action, unitType, parentRu);

        // Assert
        assertEquals(106, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValTypeParent(parentRu.getId(), unitType.getId());
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }


    @Test
    void save_shouldThrowException_whenParentNotFound() {
        // Arrange
        Long nonExistentParentId = 999L;

        // 1. Préparation du DTO
        RecordingUnitDTO recordingUnitToSave2 = new RecordingUnitDTO();
        recordingUnitToSave2.setCreatedByInstitution(new InstitutionDTO());
        RecordingUnitSummaryDTO parentRefDto = new RecordingUnitSummaryDTO();
        parentRefDto.setId(nonExistentParentId);
        recordingUnitToSave2.setParents(new HashSet<>(Set.of(parentRefDto)));

        // 2. Préparation de l'entité que le mapper va retourner
        RecordingUnit entityToSave = new RecordingUnit();
        entityToSave.setCreatedByInstitution(new Institution());
        RecordingUnit parentEntityRef = new RecordingUnit();
        parentEntityRef.setId(nonExistentParentId);
        entityToSave.setParents(new HashSet<>(Set.of(parentEntityRef)));

        // 3. Mocks
        when(recordingUnitMapper.invertConvert(recordingUnitToSave2)).thenReturn(entityToSave);

        // setupParents charge les parents via findAllById (plus findById unitaire)
        when(recordingUnitRepository.findAllById(List.of(nonExistentParentId))).thenReturn(Collections.emptyList());

        // Act & Assert
        FailedRecordingUnitSaveException exception = assertThrows(
                FailedRecordingUnitSaveException.class,
                () -> recordingUnitService.save(recordingUnitToSave2)
        );

        verify(recordingUnitRepository).findAllById(List.of(nonExistentParentId));
    }


    @Test
    void save_shouldSetContributors() {
        // Arrange
        Long id1 = 101L;
        Long id2 = 102L;

        Person person1 = new Person();
        person1.setId(id1);
        Person person2 = new Person();
        person2.setId(id2);

        PersonDTO contributor1 = new PersonDTO();
        contributor1.setId(id1);
        PersonDTO contributor2 = new PersonDTO();
        contributor2.setId(id2);

        // Initialisation du DTO à sauvegarder
        recordingUnitToSave.setContributors(new ArrayList<>(List.of(contributor1, contributor2)));

        // Simulation du comportement du mapper : DTO -> Entity
        RecordingUnit entity = new RecordingUnit();
        entity.setContributors(new ArrayList<>(List.of(person1, person2)));
        when(recordingUnitMapper.invertConvert(any(RecordingUnitDTO.class))).thenReturn(entity);

        // Simulation du repository Person (utilisé dans setupSpatialUnit)
        List<Long> contributorIds = List.of(id1, id2);

        when(personRepository.findAllById(argThat((List<Long> list) -> list != null && list.containsAll(contributorIds))))
                .thenReturn(List.of(person1, person2));

        // Simulation de la sauvegarde
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        // Simulation du mapping retour : Entity -> DTO
        when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(recordingUnitToSave);

        // Act
        RecordingUnitDTO result = recordingUnitService.save(recordingUnitToSave);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getContributors().size());
        verify(recordingUnitRepository).save(any(RecordingUnit.class));
    }

    @Test
    void fullIdentifierAlreadyExistInAction_returnTrue_whenUnitIsNewAndIdentifierExists() {
        // Arrange
        RecordingUnitDTO newUnit = new RecordingUnitDTO(); // ID is null
        newUnit.setActionUnit(new ActionUnitSummaryDTO());

        newUnit.setFullIdentifier("test");
        newUnit.setActionUnit(a1);

        RecordingUnit existingUnit = new RecordingUnit();
        existingUnit.setId(2L);

        existingUnit.setFullIdentifier("test");

        when(recordingUnitRepository.findByFullIdentifierAndActionUnitId(anyString(), anyLong()))
                .thenReturn(List.of(existingUnit));

        // Act & Assert
        assertTrue(recordingUnitService.fullIdentifierAlreadyExistInAction(newUnit));
    }


    @Test
    void createOrGetInfoOf_shouldReturnExistingInfo_whenFound() {
        // Arrange
        RecordingUnit recordingUnit = new RecordingUnit();
        recordingUnit.setId(1L);
        RecordingUnitIdInfo existingInfo = new RecordingUnitIdInfo();
        existingInfo.setRecordingUnitId(1L);

        when(recordingUnitIdInfoRepository.findById(1L)).thenReturn(Optional.of(existingInfo));

        // Act
        RecordingUnitIdInfo result = recordingUnitService.createOrGetInfoOf(recordingUnit, null);

        // Assert
        assertSame(existingInfo, result);
        verify(recordingUnitIdInfoRepository, never()).save(any(RecordingUnitIdInfo.class));
    }

    @Test
    void createOrGetInfoOf_shouldCreateAndSaveNewInfo_whenNotFound() {
        // Arrange
        ActionUnit actionUnit2;
        actionUnit2 = new ActionUnit();
        actionUnit2.setIdentifier("2025");
        actionUnit2.setMinRecordingUnitCode(5);
        actionUnit2.setId(1L);
        actionUnit2.setMaxRecordingUnitCode(5);
        Institution parentInstitution = new Institution();
        parentInstitution.setIdentifier("MOM");
        actionUnit2.setCreatedByInstitution(parentInstitution);
        RecordingUnit recordingUnit = new RecordingUnit();
        recordingUnit.setId(1L);
        recordingUnit.setActionUnit(actionUnit2);
        SpatialUnit su = new SpatialUnit();
        su.setId(99L);
        recordingUnit.setSpatialUnit(su);

        RecordingUnit parentUnit = new RecordingUnit();
        parentUnit.setId(2L);
        Concept parentType = new Concept();
        parentUnit.setType(parentType);

        when(recordingUnitIdInfoRepository.findById(1L)).thenReturn(Optional.empty());
        when(recordingUnitIdInfoRepository.save(any(RecordingUnitIdInfo.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RecordingUnitIdInfo result = recordingUnitService.createOrGetInfoOf(recordingUnit, parentUnit);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getRecordingUnitId());
        assertSame(recordingUnit, result.getRecordingUnit());
        assertEquals(99, result.getSpatialUnitNumber());
        assertSame(actionUnit2, result.getActionUnit());
        assertSame(parentUnit, result.getParent());
        assertSame(parentType, result.getRuParentType());
        verify(recordingUnitIdInfoRepository).save(result);
    }

    @Nested
    @DisplayName("generateFullIdentifier tests")
    class GenerateFullIdentifierTest {

        private RecordingUnit recordingUnitJpa;
        private ActionUnit actionUnitJpa;

        @BeforeEach
        void setUp() {
            // Initialisation des entités JPA
            recordingUnitJpa = new RecordingUnit();
            recordingUnitJpa.setId(99L);
            recordingUnitJpa.setParents(new HashSet<>());

            actionUnitJpa = new ActionUnit();
            actionUnitJpa.setId(1L);


        }

        @Test
        @DisplayName("should return numerical id when format is null")
        void generateFullIdentifier_withNullFormat_shouldReturnNumericalId() {
            // 1. Arrange - Préparation des entités avec des IDs cohérents
            Long targetRuId = 99L;
            Long targetAuId = 1L;

            // L'unité qui sera "sauvegardée" (issue du mapper)
            RecordingUnit ruJpa = new RecordingUnit();
            ruJpa.setId(targetRuId);

            // L'unité d'action qui porte le format
            ActionUnit auJpa = new ActionUnit();
            auJpa.setId(targetAuId);
            auJpa.setRecordingUnitIdentifierFormat(null); // Cas testé

            // Mock des mappers : indispensable car le service commence par convertir les DTOs
            when(recordingUnitMapper.invertConvert(recordingUnitToSave)).thenReturn(ruJpa);
            when(actionUnitSummaryMapper.invertConvert(any())).thenReturn(auJpa);

            // Mock du repository d'info : on simule qu'aucune info n'existe encore
            when(recordingUnitIdInfoRepository.findById(targetRuId)).thenReturn(Optional.empty());
            when(recordingUnitIdInfoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            when(recordingUnitIdCounterRepository.ruNextValUnique(anyLong())).thenReturn(10);

            // 2. Act
            String identifier = recordingUnitService.generateFullIdentifier(actionUnit, recordingUnitToSave);

            // 3. Assert
            assertEquals("10", identifier, "Le service devrait retourner la valeur du compteur quand le format est null");
        }

        @Test
        @DisplayName("should return formatted identifier when using resolvers")
        void generateFullIdentifier_withResolvers_shouldReturnFormattedIdentifier() {
            // Arrange
            RecordingUnitService spiedService = spy(recordingUnitService);
            actionUnitJpa.setRecordingUnitIdentifierFormat("{MOCK}-{NUM_UE}");

            RuIdentifierResolver mockResolver = mock(RuIdentifierResolver.class);

            when(mockResolver.formatUsesThisResolver(anyString())).thenAnswer(inv -> ((String)inv.getArgument(0)).contains("{MOCK}"));
            when(mockResolver.resolve(anyString(), any(RecordingUnitIdInfo.class))).thenAnswer(inv -> ((String)inv.getArgument(0)).replace("{MOCK}", "RESOLVED"));

            // Utilisation d'un mock pour le resolver numérique ou l'instance réelle
            RuIdentifierResolver numResolver = mock(RuIdentifierResolver.class);
            when(recordingUnitMapper.invertConvert(recordingUnitToSave)).thenReturn(recordingUnitJpa);
            // Mock systématique des mappers pour que le service travaille sur nos entités JPA

            when(actionUnitSummaryMapper.invertConvert(any(ActionUnitSummaryDTO.class))).thenReturn(actionUnitJpa);

            // Mock par défaut du repository d'info

            when(recordingUnitIdInfoRepository.findById(99L)).thenReturn(Optional.empty());
            when(recordingUnitIdInfoRepository.save(any(RecordingUnitIdInfo.class))).thenAnswer(inv -> inv.getArgument(0));
            when(numResolver.formatUsesThisResolver(anyString())).thenAnswer(inv -> ((String)inv.getArgument(0)).contains("{NUM_UE}"));
            when(numResolver.resolve(anyString(), any(RecordingUnitIdInfo.class))).thenReturn("RESOLVED-042");

            Map<String, RuIdentifierResolver> resolvers = new LinkedHashMap<>();
            resolvers.put("MOCK", mockResolver);
            resolvers.put("NUM_UE", numResolver);
            doReturn(resolvers).when(spiedService).findAllIdentifierResolver();

            when(recordingUnitIdCounterRepository.ruNextValUnique(anyLong())).thenReturn(42);

            // Act
            String identifier = spiedService.generateFullIdentifier(actionUnit, recordingUnitToSave);

            // Assert
            assertEquals("RESOLVED-042", identifier);
        }

        @Test
        @DisplayName("should use parent info when parent is present")
        void generateFullIdentifier_withParent_shouldUseParentForIdGeneration() {
            // Arrange
            RecordingUnitService spiedService = spy(recordingUnitService);

            RecordingUnit parentRu = new RecordingUnit();
            parentRu.setId(5L);
            recordingUnitJpa.getParents().add(parentRu); // Ajout à l'entité JPA utilisée par le service

            actionUnitJpa.setRecordingUnitIdentifierFormat("{NUM_PARENT}-{NUM_UE}");

            // Mock des resolvers
            RuIdentifierResolver parentRes = mock(RuIdentifierResolver.class);
            // Mock systématique des mappers pour que le service travaille sur nos entités JPA

            when(actionUnitSummaryMapper.invertConvert(any(ActionUnitSummaryDTO.class))).thenReturn(actionUnitJpa);

            // Mock par défaut du repository d'info

            when(recordingUnitIdInfoRepository.findById(99L)).thenReturn(Optional.empty());
            when(parentRes.formatUsesThisResolver(anyString())).thenReturn(true);
            when(parentRes.resolve(anyString(), any())).thenReturn("99-{NUM_UE}");
            when(recordingUnitMapper.invertConvert(recordingUnitToSave)).thenReturn(recordingUnitJpa);
            when(recordingUnitIdInfoRepository.save(any(RecordingUnitIdInfo.class))).thenAnswer(inv -> inv.getArgument(0));
            RuIdentifierResolver ueRes = mock(RuIdentifierResolver.class);

            when(ueRes.formatUsesThisResolver(anyString())).thenReturn(true);
            when(ueRes.resolve(anyString(), any())).thenReturn("99-7");

            Map<String, RuIdentifierResolver> resolvers = new LinkedHashMap<>();
            resolvers.put("NUM_PARENT", parentRes);
            resolvers.put("NUM_UE", ueRes);

            doReturn(resolvers).when(spiedService).findAllIdentifierResolver();
            when(recordingUnitIdCounterRepository.ruNextValParent(5L)).thenReturn(7);

            // Act
            String identifier = spiedService.generateFullIdentifier(actionUnit, recordingUnitToSave);

            // Assert
            assertEquals("99-7", identifier);
            verify(recordingUnitIdCounterRepository).ruNextValParent(5L);
        }
    }

    @Test
    void existsRootChildrenByAction_ShouldReturnTrue_WhenChildrenExist() {
        // Arrange
        Long actionId = 1L;
        when(recordingUnitRepository.existsRootChildrenByAction(actionId))
                .thenReturn(true);

        // Act
        boolean result = recordingUnitService.existsRootChildrenByAction(actionId);

        // Assert
        assertTrue(result, "La méthode doit retourner true si des enfants existent.");
    }

    @Test
    void findByFullIdentifierAndInstitutionIdentifier_Found_ReturnsRecordingUnit() {
        // Arrange
        String identifier = "TestIdentifier";
        String institutionIdentifier = "TestInstitutionIdentifier";
        RecordingUnit expectedUnit = new RecordingUnit();
        expectedUnit.setFullIdentifier(identifier);

        given(recordingUnitRepository.findByFullIdentifierAndInstitutionIdentifier(identifier, institutionIdentifier))
                .willReturn(Optional.of(expectedUnit));

        // Act
        RecordingUnit result = recordingUnitService.findByFullIdentifierAndInstitutionIdentifier(identifier, institutionIdentifier);

        // Assert
        assertNotNull(result);
        assertEquals(expectedUnit, result);
    }

    @Test
    void findByFullIdentifierAndInstitutionIdentifier_NotFound_ReturnsNull() {
        // Arrange
        String identifier = "TestIdentifier";
        String institutionIdentifier = "TestInstitutionIdentifier";

        given(recordingUnitRepository.findByFullIdentifierAndInstitutionIdentifier(identifier, institutionIdentifier))
                .willReturn(Optional.empty());

        // Act
        RecordingUnit result = recordingUnitService.findByFullIdentifierAndInstitutionIdentifier(identifier, institutionIdentifier);

        // Assert
        assertNull(result);
    }

    @Test
    void findByFullIdentifierAndInstitutionId_Found_ReturnsRecordingUnit() {
        // Arrange
        String fullIdentifier = "TestFullIdentifier";
        String institutionId = "test";
        RecordingUnit expectedUnit = new RecordingUnit();
        expectedUnit.setFullIdentifier(fullIdentifier);

        given(recordingUnitRepository.findByFullIdentifierAndInstitutionIdentifier(fullIdentifier, institutionId))
                .willReturn(Optional.of(expectedUnit));

        // Act
        RecordingUnit result = recordingUnitService.findByFullIdentifierAndInstitutionIdentifier(fullIdentifier, institutionId);

        // Assert
        assertNotNull(result);
        assertEquals(expectedUnit, result);
    }

    @Test
    void should_return_all_identifier_codes() {
        // Given
        Map<String, RuIdentifierResolver> mockMap = Map.of(
                "ALPHA", mock(RuIdentifierResolver.class),
                "NUM", mock(RuNumericalIdentifierResolver.class)
        );
        RecordingUnitService spiedService = spy(recordingUnitService);
        doReturn(mockMap).when(spiedService).findAllIdentifierResolver();

        // When
        List<String> result = spiedService.findAllIdentifiersCode();


        assertEquals(2, result.size(), "The list size is incorrect");
        assertTrue(result.contains("ALPHA"));
        assertTrue(result.contains("NUM"));
    }

    @Test
    void should_filter_only_numerical_identifiers() {
        // Given
        RuIdentifierResolver alphaResolver = mock(RuIdentifierResolver.class);
        RecordingUnitService spiedService = spy(recordingUnitService);
        // We must mock the concrete Numerical class for isAssignableFrom to work
        RuNumericalIdentifierResolver numResolver = mock(RuNumericalIdentifierResolver.class);
        when(numResolver.getCode()).thenReturn("NUM_CODE");

        Map<String, RuIdentifierResolver> mockMap = Map.of(
                "ALPHA", alphaResolver,
                "NUM", numResolver
        );
        doReturn(mockMap).when(spiedService).findAllIdentifierResolver();

        // When
        List<String> result = spiedService.findAllNumericalIdentifiersCode();

        // Then
        assertEquals(1, result.size(), "The list size is incorrect");
        assertTrue(result.contains("NUM_CODE"), "The list should contain NUM_CODE");
    }

    // --- Tests for findNextByActionUnit ---

    @Test
    void testFindNextByActionUnit_ShouldReturnNextUnit() {
        // Arrange
        ActionUnitSummaryDTO action = new ActionUnitSummaryDTO();
        action.setId(10L);
        RecordingUnitDTO current = new RecordingUnitDTO();
        current.setCreationTime(NOW);

        RecordingUnit nextEntity = new RecordingUnit();
        nextEntity.setId(42L);

        when(recordingUnitRepository.findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(eq(10L), any()))
                .thenReturn(Optional.of(nextEntity));

        // Act
        Long result = recordingUnitService.findNextIdByActionUnit(action, current);

        // Assert
        assertEquals(42L, result);
        verify(recordingUnitRepository, never()).findFirstByActionUnitIdOrderByCreationTimeAsc(anyLong());
        verify(recordingUnitMapper, never()).convert(any(RecordingUnit.class));
    }

    @Test
    void testFindNextByActionUnit_ShouldWrapAround() {
        // Arrange
        ActionUnitSummaryDTO action = new ActionUnitSummaryDTO();
        action.setId(10L);
        RecordingUnitDTO current = new RecordingUnitDTO();

        RecordingUnit oldestEntity = new RecordingUnit();
        oldestEntity.setId(7L);

        when(recordingUnitRepository.findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(anyLong(), any()))
                .thenReturn(Optional.empty());
        when(recordingUnitRepository.findFirstByActionUnitIdOrderByCreationTimeAsc(10L))
                .thenReturn(Optional.of(oldestEntity));

        // Act
        Long result = recordingUnitService.findNextIdByActionUnit(action, current);

        // Assert
        assertEquals(7L, result);
    }

    // --- Tests for findPreviousIdByActionUnit ---

    @Test
    void testFindPreviousByActionUnit_ShouldReturnPreviousUnit() {
        // Arrange
        ActionUnitSummaryDTO action = new ActionUnitSummaryDTO();
        action.setId(10L);
        RecordingUnitDTO current = new RecordingUnitDTO();
        current.setCreationTime(NOW);

        RecordingUnit prevEntity = new RecordingUnit();
        prevEntity.setId(41L);

        when(recordingUnitRepository.findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(eq(10L), any()))
                .thenReturn(Optional.of(prevEntity));

        // Act
        Long result = recordingUnitService.findPreviousIdByActionUnit(action, current);

        // Assert
        assertEquals(41L, result);
    }

    @Test
    void testFindPreviousByActionUnit_ShouldWrapAround() {
        // Arrange
        ActionUnitSummaryDTO action = new ActionUnitSummaryDTO();
        action.setId(10L);
        RecordingUnitDTO current = new RecordingUnitDTO();

        RecordingUnit mostRecentEntity = new RecordingUnit();
        mostRecentEntity.setId(99L);

        when(recordingUnitRepository.findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(anyLong(), any()))
                .thenReturn(Optional.empty());
        when(recordingUnitRepository.findFirstByActionUnitIdOrderByCreationTimeDesc(10L))
                .thenReturn(Optional.of(mostRecentEntity));

        // Act
        Long result = recordingUnitService.findPreviousIdByActionUnit(action, current);

        // Assert
        assertEquals(99L, result);
    }

    // --- Tests for toggleValidated ---

    @Test
    void testToggleValidated_ShouldCycleThroughStatuses() {
        // Arrange
        Long id = 1L;
        RecordingUnit unit = new RecordingUnit();
        unit.setId(id);

        when(recordingUnitRepository.findById(id)).thenReturn(Optional.of(unit));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(i -> i.getArguments()[0]);
        when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(new RecordingUnitDTO());

        // Test Cycle: INCOMPLETE -> COMPLETE
        unit.setValidated(ValidationStatus.INCOMPLETE);
        recordingUnitService.toggleValidated(id);
        assertEquals(ValidationStatus.COMPLETE, unit.getValidated());

        // Test Cycle: COMPLETE -> VALIDATED
        recordingUnitService.toggleValidated(id);
        assertEquals(ValidationStatus.VALIDATED, unit.getValidated());

        // Test Cycle: VALIDATED -> INCOMPLETE
        recordingUnitService.toggleValidated(id);
        assertEquals(ValidationStatus.INCOMPLETE, unit.getValidated());
    }

    @Test
    void testToggleValidated_NotFound_ThrowsException() {
        // Arrange
        when(recordingUnitRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecordingUnitNotFoundException.class, () -> recordingUnitService.toggleValidated(99L));
    }

    // ------------------------------------------------------------------
    // existsXxx — false branches
    // ------------------------------------------------------------------

    @Test
    void existsChildrenByParentAndInstitution_returnsFalseWhenRepositoryDoes() {
        when(recordingUnitRepository.existsChildrenByParentAndInstitution(1L, 2L)).thenReturn(false);
        assertFalse(recordingUnitService.existsChildrenByParentAndInstitution(1L, 2L));
    }

    @Test
    void existsRootChildrenByInstitution_returnsFalseWhenRepositoryDoes() {
        when(recordingUnitRepository.existsRootChildrenByInstitution(1L)).thenReturn(false);
        assertFalse(recordingUnitService.existsRootChildrenByInstitution(1L));
    }

    @Test
    void existsRootChildrenByAction_returnsFalseWhenRepositoryDoes() {
        when(recordingUnitRepository.existsRootChildrenByAction(1L)).thenReturn(false);
        assertFalse(recordingUnitService.existsRootChildrenByAction(1L));
    }

    // ------------------------------------------------------------------
    // findAllByParentRecordingUnit
    // ------------------------------------------------------------------

    @Test
    void findAllByParentRecordingUnit_mapsToDtos() {
        // RecordingUnit#equals keys on fullIdentifier — both fixtures share null,
        // so a per-instance answer keeps the stubs from collapsing onto one DTO.
        when(recordingUnitRepository.findChildrensOf(1L)).thenReturn(List.of(recordingUnit1, recordingUnit2));
        when(recordingUnitMapper.convert(any(RecordingUnit.class)))
                .thenAnswer(inv -> inv.getArgument(0) == recordingUnit1 ? recordingUnit1DTO : recordingUnit2DTO);

        List<RecordingUnitDTO> result = recordingUnitService.findAllByParentRecordingUnit(1L);

        assertEquals(2, result.size());
        assertSame(recordingUnit1DTO, result.get(0));
        assertSame(recordingUnit2DTO, result.get(1));
    }

    // ------------------------------------------------------------------
    // searchRecordingUnit / searchRecordingUnitInActionUnit / count*
    // ------------------------------------------------------------------

    @Test
    void searchRecordingUnit_returnsMappedPage() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(false);
        when(recordingUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(recordingUnitMapper.toLightDto(recordingUnit1)).thenReturn(recordingUnit1DTO);
        when(recordingUnitMapper.toLightDto(recordingUnit2)).thenReturn(recordingUnit2DTO);

        Page<RecordingUnitDTO> result = recordingUnitService.searchRecordingUnit(inst, filters, pageable);

        assertEquals(2, result.getContent().size());
    }

    @Test
    void searchRecordingUnit_rootOnlyWithoutFilters_returnsMappedPage() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        when(recordingUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(recordingUnitMapper.toLightDto(any(RecordingUnit.class))).thenReturn(recordingUnit1DTO);

        Page<RecordingUnitDTO> result = recordingUnitService.searchRecordingUnit(inst, filters, pageable);

        assertEquals(2, result.getContent().size());
    }

    @Test
    void searchRecordingUnit_rootOnlyWithFiltersAndMatches_runsClosureBranch() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(RecordingUnitSpec.FULL_IDENTIFIER, "x", FilterDTO.FilterType.CONTAINS);

        when(recordingUnitRepository.findAll(any(Specification.class))).thenReturn(List.of(recordingUnit1, recordingUnit2));
        when(recordingUnitRepository.findAncestorClosure(any(Long[].class))).thenReturn(List.of(1L, 2L));
        when(recordingUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(recordingUnitMapper.toLightDto(any(RecordingUnit.class))).thenReturn(recordingUnit1DTO);

        Page<RecordingUnitDTO> result = recordingUnitService.searchRecordingUnit(inst, filters, pageable);

        assertEquals(2, result.getContent().size());
        assertEquals(Set.of(1L, 2L), filters.getMatchIds());
    }

    @Test
    void searchRecordingUnit_rootOnlyWithFiltersNoMatches_returnsEmpty() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(RecordingUnitSpec.FULL_IDENTIFIER, "nope", FilterDTO.FilterType.CONTAINS);

        when(recordingUnitRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(recordingUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        Page<RecordingUnitDTO> result = recordingUnitService.searchRecordingUnit(inst, filters, pageable);

        assertTrue(result.getContent().isEmpty());
        verify(recordingUnitRepository, never()).findAncestorClosure(any(Long[].class));
    }

    @Test
    void searchRecordingUnitInActionUnit_returnsMappedPage() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(7L);
        FilterDTO filters = new FilterDTO(false);

        when(recordingUnitRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(recordingUnit1DTO);

        Page<RecordingUnitDTO> result = recordingUnitService.searchRecordingUnitInActionUnit(inst, au, filters, pageable);

        assertEquals(2, result.getContent().size());
    }

    @Test
    void countSearchResultsInActionUnit_delegatesToRepository() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(7L);
        FilterDTO filters = new FilterDTO(false);

        when(recordingUnitRepository.count(any(Specification.class))).thenReturn(11L);

        assertEquals(11, recordingUnitService.countSearchResultsInActionUnit(inst, au, filters));
    }

    @Test
    void countSearchResults_delegatesToRepository() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(false);
        when(recordingUnitRepository.count(any(Specification.class))).thenReturn(13L);

        assertEquals(13, recordingUnitService.countSearchResults(inst, filters));
    }

    // ------------------------------------------------------------------
    // prepareSpecs branches
    // ------------------------------------------------------------------

    @Test
    void prepareSpecs_userMode_returnsNonNullSpec() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(false);
        assertNotNull(recordingUnitService.prepareSpecs(inst, filters));
    }

    @Test
    void prepareSpecs_rootOnlyNoUserFilters_returnsNonNullSpec() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        assertNotNull(recordingUnitService.prepareSpecs(inst, filters));
    }

    @Test
    void prepareSpecs_rootOnlyWithFiltersAndPrecomputedClosure_skipsRepoLookup() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(RecordingUnitSpec.FULL_IDENTIFIER, "x", FilterDTO.FilterType.CONTAINS);
        filters.setAncestorClosure(List.of(1L, 5L));

        Specification<RecordingUnit> spec = recordingUnitService.prepareSpecs(inst, filters);

        assertNotNull(spec);
        verify(recordingUnitRepository, never()).findAll(any(Specification.class));
    }

    // ------------------------------------------------------------------
    // userFilterSpecs — each branch
    // ------------------------------------------------------------------

    @Test
    void userFilterSpecs_emptyFilter_returnsNonNullSpec() {
        FilterDTO filters = new FilterDTO(false);
        assertNotNull(RecordingUnitService.userFilterSpecs(filters));
    }

    @Test
    void userFilterSpecs_allBranchesCovered() {
        FilterDTO filters = new FilterDTO(false);
        filters.add(RecordingUnitSpec.FULL_IDENTIFIER, "ru", FilterDTO.FilterType.CONTAINS);
        filters.add(RecordingUnitSpec.AUTHOR_FILTER, List.of(1L), FilterDTO.FilterType.CONTAINS);
        filters.add(RecordingUnitSpec.MATRIX_FILTER, "loam", FilterDTO.FilterType.CONTAINS);
        filters.add(RecordingUnitSpec.SPATIAL_UNIT_FILTER, List.of(2L), FilterDTO.FilterType.CONTAINS);
        filters.add(RecordingUnitSpec.ACTION_UNIT_FILTER, List.of(3L), FilterDTO.FilterType.CONTAINS);
        filters.add(RecordingUnitSpec.CONTRIBUTORS_FILTER, List.of(4L), FilterDTO.FilterType.CONTAINS);
        filters.add(RecordingUnitSpec.TYPE_FILTER, List.of(5L), FilterDTO.FilterType.CONTAINS);
        filters.add(RecordingUnitSpec.OPENING_DATE_FILTER, List.of(NOW, NOW.plusDays(1)),
                FilterDTO.FilterType.CONTAINS);
        filters.add(RecordingUnitSpec.CLOSING_DATE_FILTER, List.of(NOW, NOW.plusDays(1)),
                FilterDTO.FilterType.CONTAINS);

        Specification<RecordingUnit> spec = RecordingUnitService.userFilterSpecs(filters);

        assertNotNull(spec);
    }

    // ------------------------------------------------------------------
    // computeAncestorClosure — three branches
    // ------------------------------------------------------------------

    @Test
    void computeAncestorClosure_notRootOnly_returnsEmpty() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(false);

        Set<Long> result = recordingUnitService.computeAncestorClosure(inst, filters);

        assertTrue(result.isEmpty());
        verifyNoInteractions(recordingUnitRepository);
    }

    @Test
    void computeAncestorClosure_rootOnlyNoUserFilters_returnsEmpty() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);

        Set<Long> result = recordingUnitService.computeAncestorClosure(inst, filters);

        assertTrue(result.isEmpty());
        verifyNoInteractions(recordingUnitRepository);
    }

    @Test
    void computeAncestorClosure_rootOnlyWithFilters_returnsClosure() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(RecordingUnitSpec.FULL_IDENTIFIER, "x", FilterDTO.FilterType.CONTAINS);

        when(recordingUnitRepository.findAll(any(Specification.class))).thenReturn(List.of(recordingUnit1));
        when(recordingUnitRepository.findAncestorClosure(any(Long[].class))).thenReturn(List.of(1L, 2L));

        Set<Long> result = recordingUnitService.computeAncestorClosure(inst, filters);

        assertEquals(Set.of(1L, 2L), result);
    }

    @Test
    void computeAncestorClosure_reusesCachedAncestorClosureFromFilter() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(1L);
        FilterDTO filters = new FilterDTO(true);
        filters.add(RecordingUnitSpec.FULL_IDENTIFIER, "x", FilterDTO.FilterType.CONTAINS);
        filters.setAncestorClosure(List.of(7L, 8L));

        Set<Long> result = recordingUnitService.computeAncestorClosure(inst, filters);

        assertEquals(Set.of(7L, 8L), result);
        verify(recordingUnitRepository, never()).findAll(any(Specification.class));
        verify(recordingUnitRepository, never()).findAncestorClosure(any(Long[].class));
    }

    @Test
    @DisplayName("Should remove child from parent's collection when DTO omits existing parent")
    void save_ShouldRemoveLinkFromOldParent() {
        // 1. SETUP: Identifiers
        Long childId = 1L;
        Long parentId = 99L;

        // 2. SETUP: Existing state in the "database"
        RecordingUnit parentEntity = new RecordingUnit();
        parentEntity.setId(parentId);
        parentEntity.setChildren(new HashSet<>());

        RecordingUnit existingManagedUnit = new RecordingUnit();
        existingManagedUnit.setId(childId);

        // Establish bi-directional link
        existingManagedUnit.setParents(new HashSet<>(Set.of(parentEntity)));
        parentEntity.getChildren().add(existingManagedUnit);

        // 3. SETUP: Incoming DTO and mapped entity
        RecordingUnitDTO inputDto = new RecordingUnitDTO();
        inputDto.setId(childId);
        inputDto.setParents(new HashSet<>()); // Requesting removal of all parents

        RecordingUnit mappedFromDto = new RecordingUnit();
        mappedFromDto.setId(childId);
        mappedFromDto.setParents(new HashSet<>());

        // 4. MOCKING: Mapper
        when(recordingUnitMapper.invertConvert(inputDto)).thenReturn(mappedFromDto);
        when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(inputDto);

        // 5. MOCKING: Repository
        // This is what newOrGetRecordingUnit calls
        when(recordingUnitRepository.findById(childId)).thenReturn(Optional.of(existingManagedUnit));

        // Final save mocks
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(i -> i.getArgument(0));

        // 6. EXECUTE
        recordingUnitService.save(inputDto);

        // 7. VERIFY: The statement in the 'Remove' loop was executed
        // Check that Parent A no longer has the child in its set
        assertFalse(parentEntity.getChildren().contains(existingManagedUnit),
                "Parent should have had the child removed from its collection");


        verify(recordingUnitRepository, atLeastOnce()).save(any(RecordingUnit.class));


        // Verify child unit's parent list is now empty in the managed entity
        assertTrue(existingManagedUnit.getParents().isEmpty());
    }
    @Test
    @DisplayName("Should remove child from managed unit when child ID is missing from incoming DTO")
    void save_ShouldRemoveChildFromCollection() {
        // 1. SETUP: Identifiers
        Long parentId = 1L;
        Long oldChildId = 55L;

        // 2. SETUP: Existing state (Parent has one child)
        RecordingUnit oldChild = new RecordingUnit();
        oldChild.setId(oldChildId);

        RecordingUnit existingManagedParent = new RecordingUnit();
        existingManagedParent.setId(parentId);
        existingManagedParent.setChildren(new HashSet<>(Set.of(oldChild)));
        existingManagedParent.setParents(new HashSet<>()); // Initialize to avoid NPEs

        // 3. INPUT: DTO with NO children (to trigger removeIf)
        RecordingUnitDTO inputDto = new RecordingUnitDTO();
        inputDto.setId(parentId);
        inputDto.setChildren(new HashSet<>()); // Empty children list

        RecordingUnit mappedFromDto = new RecordingUnit();
        mappedFromDto.setId(parentId);
        mappedFromDto.setChildren(new HashSet<>());

        // 4. MOCKING
        when(recordingUnitMapper.invertConvert(inputDto)).thenReturn(mappedFromDto);
        when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(inputDto);

        // Mock finding the parent in the database
        when(recordingUnitRepository.findById(parentId)).thenReturn(Optional.of(existingManagedParent));

        // Mock the save calls
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(i -> i.getArgument(0));

        // 5. EXECUTE
        recordingUnitService.save(inputDto);

        // 6. VERIFY: The removeIf statement was effective

        // Ensure the old child is no longer present specifically
        assertFalse(existingManagedParent.getChildren().stream()
                .anyMatch(c -> c.getId().equals(oldChildId)));
    }

    @Test
    @DisplayName("Should add new child to managed unit when ID is present in DTO but not in managed entity")
    void save_ShouldAddNewChildToCollection() {
        // 1. SETUP: Identifiers
        Long parentId = 1L;
        Long newChildId = 100L;

        // 2. SETUP: Managed state (Parent starts with no children)
        RecordingUnit managedParent = new RecordingUnit();
        managedParent.setId(parentId);
        managedParent.setChildren(new HashSet<>()); // Empty children set
        managedParent.setParents(new HashSet<>());

        // 3. SETUP: New Child (The one to be found in DB)
        RecordingUnit newChildEntity = new RecordingUnit();
        newChildEntity.setId(newChildId);

        // 4. INPUT: DTO containing the new child ID
        RecordingUnitDTO inputDto = new RecordingUnitDTO();
        inputDto.setId(parentId);

        RecordingUnitSummaryDTO childDto = new RecordingUnitSummaryDTO();
        childDto.setId(newChildId);
        inputDto.setChildren(new HashSet<>(Set.of(childDto)));

        // Mapping setup
        RecordingUnit mappedFromDto = new RecordingUnit();
        mappedFromDto.setId(parentId);
        mappedFromDto.setChildren(new HashSet<>(Set.of(newChildEntity)));

        // 5. MOCKING
        when(recordingUnitMapper.invertConvert(inputDto)).thenReturn(mappedFromDto);
        when(recordingUnitMapper.convert(any(RecordingUnit.class))).thenReturn(inputDto);

        // Mock finding the parent
        when(recordingUnitRepository.findById(parentId)).thenReturn(Optional.of(managedParent));

        // setupChilds résout les enfants manquants via findAllById
        when(recordingUnitRepository.findAllById(argThat(ids -> {
            List<Long> collected = new ArrayList<>();
            ids.forEach(collected::add);
            return collected.size() == 1 && collected.contains(newChildId);
        }))).thenReturn(List.of(newChildEntity));

        // Mock save
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(i -> i.getArgument(0));

        // 6. EXECUTE
        recordingUnitService.save(inputDto);

        // 7. VERIFY: The "Add" logic was executed
        // Check that the child was added to the managed collection

        assertEquals(1, managedParent.getChildren().size());

        verify(recordingUnitRepository).findAllById(argThat(ids -> {
            List<Long> collected = new ArrayList<>();
            ids.forEach(collected::add);
            return collected.size() == 1 && collected.contains(newChildId);
        }));
    }

    @Nested
    @DisplayName("OpenAPI access and lifecycle")
    class OpenApiAccessAndLifecycle {

        @Test
        void findAccessibleRecordingUnitByKey_numericId_inScope() {
            InstitutionDTO instDto = new InstitutionDTO();
            instDto.setId(100L);
            RecordingUnit entity = new RecordingUnit();
            entity.setId(42L);
            Institution inst = new Institution();
            inst.setId(100L);
            entity.setCreatedByInstitution(inst);

            RecordingUnitDTO dto1 = new RecordingUnitDTO();
            dto1.setId(42L);
            dto1.setCreatedByInstitution(instDto);

            when(recordingUnitRepository.findById(42L)).thenReturn(Optional.of(entity));
            when(recordingUnitMapper.convert(entity)).thenReturn(dto1);

            RecordingUnitDTO result = recordingUnitService.findAccessibleRecordingUnitByKey(
                    "42", Set.of(100L), null);

            assertEquals(42L, result.getId());
            verify(recordingUnitRepository, never()).findFirstByFullIdentifierAndInstitutionIdIn(any(), any());
        }

        @Test
        void findAccessibleRecordingUnitWithEntity_loadsSpecimenCount() {
            InstitutionDTO instDto = new InstitutionDTO();
            instDto.setId(100L);
            RecordingUnit entity = new RecordingUnit();
            entity.setId(7L);
            Institution inst = new Institution();
            inst.setId(100L);
            entity.setCreatedByInstitution(inst);

            RecordingUnitDTO dto1 = new RecordingUnitDTO();
            dto1.setId(7L);
            dto1.setCreatedByInstitution(instDto);

            when(recordingUnitRepository.findById(7L)).thenReturn(Optional.of(entity));
            when(recordingUnitMapper.convert(entity)).thenReturn(dto1);
            when(recordingUnitRepository.countSpecimensByRecordingUnitId(7L)).thenReturn(4L);

            var accessible = recordingUnitService.findAccessibleRecordingUnitWithEntity(
                    "7", Set.of(100L), List.of("specimen"));

            assertEquals(4L, accessible.dto().getSpecimenCount());
            assertSame(entity, accessible.entity());
        }

        @Test
        void findAccessibleRecordingUnitByKey_emptyScope_throwsNotFound() {
            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.findAccessibleRecordingUnitByKey("1", Set.of(), null));
        }

        @Test
        void findAccessibleRecordingUnitByKey_blankKey_throwsNotFound() {
            Set<Long> institutionIds = Set.of(1L);
            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.findAccessibleRecordingUnitByKey("  ", institutionIds, null));
        }

        @Test
        void findAccessibleRecordingUnitByKey_nonNumeric_resolvesByFullIdentifier() {
            RecordingUnit entity = new RecordingUnit();
            entity.setId(9L);
            RecordingUnitDTO dto1 = new RecordingUnitDTO();
            dto1.setId(9L);
            InstitutionDTO instDto = new InstitutionDTO();
            instDto.setId(1L);
            dto1.setCreatedByInstitution(instDto);

            when(recordingUnitRepository.findFirstByFullIdentifierAndInstitutionIdIn("UE-ABC", Set.of(1L)))
                    .thenReturn(Optional.of(entity));
            when(recordingUnitMapper.convert(entity)).thenReturn(dto1);

            RecordingUnitDTO result = recordingUnitService.findAccessibleRecordingUnitByKey(
                    "UE-ABC", Set.of(1L), null);

            assertEquals(9L, result.getId());
        }

        @Test
        void requireAccessibleRecordingUnitByPrimaryKey_success() {
            InstitutionDTO instDto = new InstitutionDTO();
            instDto.setId(5L);
            RecordingUnit entity = new RecordingUnit();
            entity.setId(3L);
            RecordingUnitDTO dto1 = new RecordingUnitDTO();
            dto1.setId(3L);
            dto1.setCreatedByInstitution(instDto);

            when(recordingUnitRepository.findById(3L)).thenReturn(Optional.of(entity));
            when(recordingUnitMapper.convert(entity)).thenReturn(dto1);

            RecordingUnitDTO result = recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(3L, Set.of(5L));

            assertEquals(3L, result.getId());
        }

        @Test
        void requireAccessibleRecordingUnitByPrimaryKey_outOfScope_throws() {
            RecordingUnit entity = new RecordingUnit();
            entity.setId(3L);
            RecordingUnitDTO dto1 = new RecordingUnitDTO();
            dto1.setId(3L);
            InstitutionDTO instDto = new InstitutionDTO();
            instDto.setId(99L);
            dto1.setCreatedByInstitution(instDto);

            when(recordingUnitRepository.findById(3L)).thenReturn(Optional.of(entity));
            when(recordingUnitMapper.convert(entity)).thenReturn(dto1);

            Set<Long> institutionIds = Set.of(5L);
            assertThrows(RecordingUnitNotFoundException.class,
                    () -> recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(3L, institutionIds));
        }

        @Test
        void findRelationsForAccessibleRecordingUnit_mapsStratigraphyAndHierarchy() {
            RecordingUnit entity = new RecordingUnit();
            entity.setId(1L);
            Institution inst = new Institution();
            inst.setId(10L);
            entity.setCreatedByInstitution(inst);

            RecordingUnit parent = new RecordingUnit();
            parent.setId(2L);
            RecordingUnit child = new RecordingUnit();
            child.setId(3L);
            entity.setParents(Set.of(parent));
            entity.setChildren(Set.of(child));

            RecordingUnitDTO dto1 = new RecordingUnitDTO();
            dto1.setId(1L);
            InstitutionDTO instDto = new InstitutionDTO();
            instDto.setId(10L);
            dto1.setCreatedByInstitution(instDto);

            StratigraphicRelationship rel = new StratigraphicRelationship();
            StratigraphicRelationshipDTO relDto = new StratigraphicRelationshipDTO();

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(recordingUnitMapper.convert(entity)).thenReturn(dto1);
            when(stratigraphicRelationshipRepository.findAllInvolvingRecordingUnitId(1L)).thenReturn(List.of(rel));
            when(stratigraphicRelationshipMapper.convert(rel)).thenReturn(relDto);
            when(recordingUnitSummaryMapper.convert(any(RecordingUnit.class))).thenAnswer(invocation -> {
                RecordingUnit ru = invocation.getArgument(0);
                RecordingUnitSummaryDTO summary = new RecordingUnitSummaryDTO();
                summary.setId(ru.getId());
                return summary;
            });

            var bundle = recordingUnitService.findRelationsForAccessibleRecordingUnit("1", Set.of(10L));

            assertEquals(1, bundle.stratigraphicRelationships().size());
            assertEquals(1, bundle.parents().size());
            assertEquals(1, bundle.children().size());
            assertEquals(2L, bundle.parents().get(0).getId());
            assertEquals(3L, bundle.children().get(0).getId());
        }

        @Test
        void findChildrenForAccessibleRecordingUnit_returnsChildSummaries() {
            RecordingUnit entity = new RecordingUnit();
            entity.setId(1L);
            Institution inst = new Institution();
            inst.setId(10L);
            entity.setCreatedByInstitution(inst);
            RecordingUnit child = new RecordingUnit();
            child.setId(5L);
            entity.setChildren(Set.of(child));

            RecordingUnitDTO dto1 = new RecordingUnitDTO();
            dto1.setId(1L);
            InstitutionDTO instDto = new InstitutionDTO();
            instDto.setId(10L);
            dto1.setCreatedByInstitution(instDto);

            RecordingUnitSummaryDTO childSummary = new RecordingUnitSummaryDTO();
            childSummary.setId(5L);

            when(recordingUnitRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(recordingUnitMapper.convert(entity)).thenReturn(dto1);
            when(recordingUnitSummaryMapper.convert(child)).thenReturn(childSummary);

            List<RecordingUnitSummaryDTO> children = recordingUnitService.findChildrenForAccessibleRecordingUnit(
                    "1", Set.of(10L));

            assertEquals(1, children.size());
            assertEquals(5L, children.get(0).getId());
        }

        @Test
        void findByActionUnitId_enrichesSpecimenAndRelationshipCounts() {
            RecordingUnit ru = new RecordingUnit();
            ru.setId(8L);
            Page<RecordingUnit> entityPage = new PageImpl<>(List.of(ru), PageRequest.of(0, 10), 1);
            RecordingUnitDTO dto1 = new RecordingUnitDTO();
            dto1.setId(8L);

            when(recordingUnitRepository.findAllByActionUnitId(eq(2L), any(Pageable.class))).thenReturn(entityPage);
            when(recordingUnitMapper.convert(ru)).thenReturn(dto1);
            when(recordingUnitRepository.countSpecimensByRecordingUnitId(8L)).thenReturn(2L);
            when(recordingUnitRepository.countStratigraphicRelationshipsByRecordingUnitId(8L)).thenReturn(3L);

            Page<RecordingUnitDTO> result = recordingUnitService.findByActionUnitId(2L, 10, 0,
                    org.springframework.data.domain.Sort.by("id"));

            assertEquals(1, result.getTotalElements());
            assertEquals(2L, result.getContent().get(0).getSpecimenCount());
            assertEquals(3L, result.getContent().get(0).getRelationshipCount());
        }

        @Test
        void findByActionIdAndFullId_delegatesToRepository() {
            RecordingUnit ru = new RecordingUnit();
            ru.setId(1L);
            when(recordingUnitRepository.findByFullIdentifierAndActionUnitId("UE-1", 3L)).thenReturn(List.of(ru));

            List<RecordingUnit> result = recordingUnitService.findByActionIdAndFullId(3L, "UE-1");

            assertEquals(1, result.size());
        }

        @Test
        void deleteRecordingUnitById_success() {
            RecordingUnit ru = new RecordingUnit();
            ru.setId(20L);
            ru.setParents(new HashSet<>());
            ru.setChildren(new HashSet<>());
            ru.setRelationshipsAsUnit1(new HashSet<>());
            ru.setRelationshipsAsUnit2(new HashSet<>());
            ru.setContributors(new ArrayList<>());
            ru.setDocuments(new HashSet<>());

            when(recordingUnitRepository.findById(20L)).thenReturn(Optional.of(ru));
            when(recordingUnitRepository.countSpecimensByRecordingUnitId(20L)).thenReturn(0L);
            when(stratigraphicRelationshipRepository.findAllInvolvingRecordingUnitId(20L)).thenReturn(List.of());
            when(recordingUnitIdInfoRepository.existsById(20L)).thenReturn(false);
            when(recordingUnitRepository.save(ru)).thenReturn(ru);

            recordingUnitService.deleteRecordingUnitById(20L);

            verify(recordingUnitRepository).deleteContributorLinksForRecordingUnit(20L);
            verify(documentRepository).deleteAllRecordingUnitDocumentLinksByRecordingUnitId(20L);
            verify(recordingUnitIdCounterRepository).deleteAllByRecordingUnitId(20L);
            verify(recordingUnitRepository).delete(ru);
        }

        @Test
        void deleteRecordingUnitById_withSpecimens_throwsIllegalState() {
            RecordingUnit ru = new RecordingUnit();
            ru.setId(20L);
            when(recordingUnitRepository.findById(20L)).thenReturn(Optional.of(ru));
            when(recordingUnitRepository.countSpecimensByRecordingUnitId(20L)).thenReturn(2L);

            assertThrows(IllegalStateException.class,
                    () -> recordingUnitService.deleteRecordingUnitById(20L));
        }

        @Test
        void deleteRecordingUnitById_withStudies_throwsIllegalState() {
            RecordingUnit ru = new RecordingUnit();
            ru.setId(20L);
            when(recordingUnitRepository.findById(20L)).thenReturn(Optional.of(ru));
            when(recordingUnitRepository.countSpecimensByRecordingUnitId(20L)).thenReturn(1L);

            assertThrows(IllegalStateException.class,
                    () -> recordingUnitService.deleteRecordingUnitById(20L));
        }

        @Test
        void deleteRecordingUnitById_withChildren_throwsIllegalState() {
            RecordingUnit ru = new RecordingUnit();
            ru.setId(20L);
            RecordingUnit child = new RecordingUnit();
            child.setId(21L);
            ru.setChildren(new HashSet<>(Set.of(child)));
            when(recordingUnitRepository.findById(20L)).thenReturn(Optional.of(ru));
            when(recordingUnitRepository.countSpecimensByRecordingUnitId(20L)).thenReturn(0L);

            assertThrows(IllegalStateException.class,
                    () -> recordingUnitService.deleteRecordingUnitById(20L));
        }
    }

}