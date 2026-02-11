package fr.siamois.domain.services;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
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
import fr.siamois.domain.services.recordingunit.StratigraphicRelationshipService;
import fr.siamois.domain.services.recordingunit.identifier.RuNumParentResolver;
import fr.siamois.domain.services.recordingunit.identifier.RuNumResolver;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuIdentifierResolver;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.infrastructure.api.dto.ConceptFieldDTO;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdCounterRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdInfoRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class RecordingUnitServiceTest {

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


    @InjectMocks
    private RecordingUnitService recordingUnitService;

    SpatialUnit spatialUnit1;
    RecordingUnit recordingUnit1;
    RecordingUnit recordingUnit2;
    Ark newArk;
    Vocabulary vocabulary;
    ConceptFieldDTO dto;
    Concept concept;


    RecordingUnit recordingUnitToSave;

    Page<RecordingUnit> page ;
    Pageable pageable;

    // For testing permission related method
    private Person user;
    private UserInfo userInfo;

    private ActionUnit actionUnit;

    @BeforeEach
    void setUp() {
        spatialUnit1 = new SpatialUnit();
        recordingUnit1 = new RecordingUnit();
        recordingUnit2 = new RecordingUnit();
        spatialUnit1.setId(1L);
        recordingUnit1.setId(1L);
        recordingUnit2.setId(2L);
        concept = new Concept();
        newArk = new Ark();
        vocabulary = new Vocabulary();
        dto = new ConceptFieldDTO();

        Institution parentInstitution = new Institution();
        parentInstitution.setIdentifier("MOM");
        actionUnit = new ActionUnit();
        actionUnit.setIdentifier("2025");
        actionUnit.setMinRecordingUnitCode(5);
        actionUnit.setId(1L);
        actionUnit.setMaxRecordingUnitCode(5);
        actionUnit.setCreatedByInstitution(parentInstitution);
        recordingUnitToSave = new RecordingUnit();
        recordingUnitToSave.setActionUnit(actionUnit);
        recordingUnitToSave.setCreatedByInstitution(parentInstitution);
        recordingUnitToSave.setFormResponse(new CustomFormResponse());

        page = new PageImpl<>(List.of(recordingUnit1, recordingUnit2));
        pageable = PageRequest.of(0, 10);


        // Permission related methods
        user = new Person();
        userInfo = new UserInfo(parentInstitution, user, "fr");


    }

    @Test
    void findById_success() {

        when(recordingUnitRepository.findById(recordingUnit1.getId())).thenReturn(Optional.ofNullable(recordingUnit1));

        // act
        RecordingUnit actualResult = recordingUnitService.findById(recordingUnit1.getId());

        // assert
        assertEquals(recordingUnit1, actualResult);
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
        Concept newType = new Concept();
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

        RecordingUnit anteriorUnit = new RecordingUnit();
        anteriorUnit.setId(1L);
        RecordingUnit synchronousUnit = new RecordingUnit();
        synchronousUnit.setId(2L);
        RecordingUnit posteriorUnit = new RecordingUnit();
        posteriorUnit.setId(3L);

        // add a parent
        RecordingUnit parent1Unit = new RecordingUnit();
        parent1Unit.setId(10L);
        parent1Unit.setFullIdentifier("p1");
        recordingUnitToSave.getParents().add(parent1Unit);

        // add a children
        RecordingUnit child1Unit = new RecordingUnit();
        child1Unit.setId(20L);
        child1Unit.setFullIdentifier("c1");
        recordingUnitToSave.getChildren().add(child1Unit);

        StratigraphicRelationship antRelationship = new StratigraphicRelationship();
        antRelationship.setUnit1(recordingUnitToSave);
        antRelationship.setUnit2(anteriorUnit);
        antRelationship.setConcept(StratigraphicRelationshipService.ASYNCHRONOUS);
        StratigraphicRelationship syncRelationship = new StratigraphicRelationship();
        syncRelationship.setUnit1(recordingUnitToSave);
        syncRelationship.setUnit2(synchronousUnit);
        syncRelationship.setConcept(StratigraphicRelationshipService.SYNCHRONOUS);
        StratigraphicRelationship postRelationship = new StratigraphicRelationship();
        postRelationship.setUnit1(posteriorUnit);
        postRelationship.setUnit2(recordingUnitToSave);
        postRelationship.setConcept(StratigraphicRelationshipService.ASYNCHRONOUS);

        Person p = new Person();

        Concept c = new Concept();
        when(conceptService.saveOrGetConcept(c)).thenReturn(c);


        when(recordingUnitRepository.save(any(RecordingUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(personRepository.findAllById(anyList())).thenReturn(List.of(p));

        when(recordingUnitRepository.findById(10L)).thenReturn(Optional.of(parent1Unit));

        RecordingUnit result = recordingUnitService.save(recordingUnitToSave,c
        );

        // assert
        assertNotNull(result);
        assertNull(result.getFormResponse());
        verify(recordingUnitRepository, times(3)).save(any(RecordingUnit.class));



    }

    @Test
    @SuppressWarnings("unchecked")
    void save_saveFormIfSet() {

        CustomForm form = new CustomForm();
        recordingUnitToSave.setFormResponse(new CustomFormResponse());
        recordingUnitToSave.getFormResponse().setForm(form);

        RecordingUnit anteriorUnit = new RecordingUnit();
        anteriorUnit.setId(1L);
        RecordingUnit synchronousUnit = new RecordingUnit();
        synchronousUnit.setId(2L);
        RecordingUnit posteriorUnit = new RecordingUnit();
        posteriorUnit.setId(3L);

        StratigraphicRelationship antRelationship = new StratigraphicRelationship();
        antRelationship.setUnit1(recordingUnitToSave);
        antRelationship.setUnit2(anteriorUnit);
        antRelationship.setConcept(StratigraphicRelationshipService.ASYNCHRONOUS);
        StratigraphicRelationship syncRelationship = new StratigraphicRelationship();
        syncRelationship.setUnit1(recordingUnitToSave);
        syncRelationship.setUnit2(synchronousUnit);
        syncRelationship.setConcept(StratigraphicRelationshipService.SYNCHRONOUS);
        StratigraphicRelationship postRelationship = new StratigraphicRelationship();
        postRelationship.setUnit1(posteriorUnit);
        postRelationship.setUnit2(recordingUnitToSave);
        postRelationship.setConcept(StratigraphicRelationshipService.ASYNCHRONOUS);

        Person p = new Person();

        Concept c = new Concept();
        when(conceptService.saveOrGetConcept(c)).thenReturn(c);

        when(personRepository.findAllById(anyList())).thenReturn(List.of(p));

        when(recordingUnitRepository.save(any(RecordingUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doNothing().when(customFormResponseService)
                .saveFormResponse(any(CustomFormResponse.class), any(CustomFormResponse.class));

        RecordingUnit res = recordingUnitService.save(recordingUnitToSave, c);
        assertNotNull(res);
        verify(customFormResponseService, times(1))
                .saveFormResponse(any(CustomFormResponse.class), any(CustomFormResponse.class));
    }

    @Test
    void save_shouldUpdateExistingUnit_whenIdIsProvided() {
        // Arrange
        long existingId = 42L;
        recordingUnitToSave.setId(existingId);
        recordingUnitToSave.setDescription("Updated description");

        RecordingUnit foundUnit = new RecordingUnit();
        foundUnit.setId(existingId);
        foundUnit.setDescription("Original description");

        when(recordingUnitRepository.findById(existingId)).thenReturn(Optional.of(foundUnit));
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conceptService.saveOrGetConcept(any(Concept.class))).thenReturn(new Concept());
        when(personRepository.findAllById(anyList())).thenReturn(List.of());

        // Act
        RecordingUnit result = recordingUnitService.save(recordingUnitToSave, new Concept());

        // Assert
        assertEquals(existingId, result.getId());
        assertEquals("Updated description", result.getDescription());
        verify(recordingUnitRepository).findById(existingId);
        // 2 saves: one in setupAdditionalAnswers, one at the end.
        verify(recordingUnitRepository, times(2)).save(any(RecordingUnit.class));
    }

    @Test
    void save_shouldCreateNewUnit_whenIdIsProvidedButNotFound() {
        // Arrange
        long nonExistentId = 43L;
        recordingUnitToSave.setId(nonExistentId);
        recordingUnitToSave.setDescription("New unit with given ID");

        when(recordingUnitRepository.findById(nonExistentId)).thenReturn(Optional.empty());
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conceptService.saveOrGetConcept(any(Concept.class))).thenReturn(new Concept());
        when(personRepository.findAllById(anyList())).thenReturn(List.of());

        // Act
        RecordingUnit result = recordingUnitService.save(recordingUnitToSave, new Concept());

        // Assert
        assertNull(result.getId());
        assertEquals("New unit with given ID", result.getDescription());
        verify(recordingUnitRepository).findById(nonExistentId);
        verify(recordingUnitRepository, times(2)).save(any(RecordingUnit.class));
    }

    @Test
    void save_shouldThrowFailedRecordingUnitSaveException_whenDependencyFails() {
        // Arrange
        when(conceptService.saveOrGetConcept(any(Concept.class))).thenThrow(new RuntimeException("Dependency error"));

        // Act & Assert
        FailedRecordingUnitSaveException exception = assertThrows(
                FailedRecordingUnitSaveException.class,
                () -> recordingUnitService.save(recordingUnitToSave, new Concept())
        );

        assertEquals("Dependency error", exception.getMessage());
    }

    @Test
    void testFindAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining_Success() {

        when(recordingUnitRepository.findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(page);

        // Act
        Page<RecordingUnit> actualResult = recordingUnitService.findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                1L, "null", new Long[2], "null", "fr", pageable
        );

        // Assert
        assertEquals(recordingUnit1, actualResult.getContent().get(0));
        assertEquals(recordingUnit2, actualResult.getContent().get(1));
    }

    @Test
    void findAllByInstitutionAndByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining_Success() {
        // Arrange
        Long institutionId = 1L;
        Long actionId = 1L;
        String fullIdentifier = "test";
        Long[] categoryIds = {1L, 2L};
        String global = "global";
        String langCode = "fr";

        when(recordingUnitRepository.findAllByInstitutionAndByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                institutionId, actionId, fullIdentifier, categoryIds, global, langCode, pageable
        )).thenReturn(page);

        // Act
        Page<RecordingUnit> result = recordingUnitService.findAllByInstitutionAndByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                institutionId, actionId, fullIdentifier, categoryIds, global, langCode, pageable
        );

        // Assert
        assertEquals(page, result);
        verify(recordingUnitRepository).findAllByInstitutionAndByActionUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                institutionId, actionId, fullIdentifier, categoryIds, global, langCode, pageable
        );
    }

    @Test
    void canCreateSpecimen_returnsTrue_whenUserIsManagerOfCreatingInstitution() {
        RecordingUnit recordingUnit;
        recordingUnit = mock(RecordingUnit.class);
        when(recordingUnit.getActionUnit()).thenReturn(actionUnit);

        when(institutionService.isManagerOf(any(Institution.class), any(Person.class))).thenReturn(true);

        boolean result = recordingUnitService.canCreateSpecimen(userInfo, recordingUnit);

        assertTrue(result);
    }

    @Test
    void canCreateSpecimen_returnsTrue_whenUserIsActionUnitManager() {
        RecordingUnit recordingUnit;
        recordingUnit = mock(RecordingUnit.class);
        when(recordingUnit.getActionUnit()).thenReturn(actionUnit);

        when(institutionService.isManagerOf(any(Institution.class), any(Person.class))).thenReturn(false);
        when(actionUnitService.isManagerOf(actionUnit, user)).thenReturn(true);

        boolean result = recordingUnitService.canCreateSpecimen(userInfo, recordingUnit);

        assertTrue(result);
    }

    @Test
    void canCreateSpecimen_returnsTrue_whenUserIsTeamMember_andActionUnitIsOngoing() {
        RecordingUnit recordingUnit;
        recordingUnit = mock(RecordingUnit.class);
        when(recordingUnit.getActionUnit()).thenReturn(actionUnit);

        when(institutionService.isManagerOf(any(Institution.class), any(Person.class))).thenReturn(false);
        when(actionUnitService.isManagerOf(actionUnit, user)).thenReturn(false);
        when(teamMemberRepository.existsByActionUnitAndPerson(actionUnit, user)).thenReturn(true);
        when(actionUnitService.isActionUnitStillOngoing(actionUnit)).thenReturn(true);

        boolean result = recordingUnitService.canCreateSpecimen(userInfo, recordingUnit);

        assertTrue(result);
    }

    @Test
    void canCreateSpecimen_returnsFalse_whenUserIsTeamMember_butActionUnitIsNotOngoing() {
        RecordingUnit recordingUnit;
        recordingUnit = mock(RecordingUnit.class);
        when(recordingUnit.getActionUnit()).thenReturn(actionUnit);

        when(institutionService.isManagerOf(any(Institution.class), any(Person.class))).thenReturn(false);
        when(actionUnitService.isManagerOf(actionUnit, user)).thenReturn(false);
        when(teamMemberRepository.existsByActionUnitAndPerson(actionUnit, user)).thenReturn(true);
        when(actionUnitService.isActionUnitStillOngoing(actionUnit)).thenReturn(false);

        boolean result = recordingUnitService.canCreateSpecimen(userInfo, recordingUnit);

        assertFalse(result);
    }

    @Test
    void canCreateSpecimen_returnsFalse_whenUserHasNoPermissions() {
        RecordingUnit recordingUnit;
        recordingUnit = mock(RecordingUnit.class);
        when(recordingUnit.getActionUnit()).thenReturn(actionUnit);

        when(institutionService.isManagerOf(any(Institution.class), any(Person.class))).thenReturn(false);
        when(actionUnitService.isManagerOf(actionUnit, user)).thenReturn(false);
        when(teamMemberRepository.existsByActionUnitAndPerson(actionUnit, user)).thenReturn(false);

        boolean result = recordingUnitService.canCreateSpecimen(userInfo, recordingUnit);

        assertFalse(result);
        verify(actionUnitService, never()).isActionUnitStillOngoing(any());
    }

    @Test
    void testFindAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining_Success() {

        when(recordingUnitRepository.findAllByChildAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(page);

        // Act
        Page<RecordingUnit> actualResult = recordingUnitService.findAllByChildAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                1L, "null", new Long[2], "null", "fr", pageable
        );

        // Assert
        assertEquals(recordingUnit1, actualResult.getContent().get(0));
        assertEquals(recordingUnit2, actualResult.getContent().get(1));
    }

    @Test
    void testFindAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining_Success() {

        when(recordingUnitRepository.findAllByParentAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(page);

        // Act
        Page<RecordingUnit> actualResult = recordingUnitService.findAllByParentAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                1L, "null", new Long[2], "null", "fr", pageable
        );

        // Assert
        assertEquals(recordingUnit1, actualResult.getContent().get(0));
        assertEquals(recordingUnit2, actualResult.getContent().get(1));
    }

    @Test
    void test_findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining_Success() {

        when(recordingUnitRepository.findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(page);

        // Act
        Page<RecordingUnit> actualResult = recordingUnitService.findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                1L, "null", new Long[2], "null", "fr", pageable
        );

        // Assert
        assertEquals(recordingUnit1, actualResult.getContent().get(0));
        assertEquals(recordingUnit2, actualResult.getContent().get(1));
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
        ActionUnit au = new ActionUnit();
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
        List<RecordingUnit> result = recordingUnitService.findAllWithoutParentsByInstitution(institutionId);

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
        List<RecordingUnit> result = recordingUnitService.findChildrenByParentAndInstitution(parentId, institutionId);

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
        List<RecordingUnit> result = recordingUnitService.findAllWithoutParentsByAction(actionId);

        // Assert
        assertEquals(2, result.size());
        verify(recordingUnitRepository).findRootsByAction(actionId);
    }

    @Test
    void fullIdentifierAlreadyExistInAction_returnTrue_whenSameIdentifierIsNotSameUnit() {
        recordingUnit1 = new RecordingUnit();
        recordingUnit1.setId(1L);
        recordingUnit1.setActionUnit(actionUnit);
        recordingUnit1.setFullIdentifier("test");

        recordingUnit2 = new RecordingUnit();
        recordingUnit2.setId(2L);
        recordingUnit2.setActionUnit(actionUnit);
        recordingUnit2.setFullIdentifier("test");

        when(recordingUnitRepository.findByFullIdentifierAndActionUnit("test", actionUnit))
                .thenReturn(List.of(recordingUnit1, recordingUnit2));

        assertTrue(recordingUnitService.fullIdentifierAlreadyExistInAction(recordingUnit1));
    }

    @Test
    void fullIdentifierAlreadyExistInAction_returnFalse_whenSameIdentifierIsSameUnit() {
        recordingUnit1 = new RecordingUnit();
        recordingUnit1.setId(1L);
        recordingUnit1.setActionUnit(actionUnit);
        recordingUnit1.setFullIdentifier("test");

        when(recordingUnitRepository.findByFullIdentifierAndActionUnit("test", actionUnit))
                .thenReturn(List.of(recordingUnit1, recordingUnit1));

        assertFalse(recordingUnitService.fullIdentifierAlreadyExistInAction(recordingUnit1));
    }

    @Test
    void fullIdentifierAlreadyExistInAction_returnFalse_whenIdentifierDoesntAlreadyExists() {
        recordingUnit1 = new RecordingUnit();
        recordingUnit1.setId(1L);
        recordingUnit1.setActionUnit(actionUnit);
        recordingUnit1.setFullIdentifier("test");

        when(recordingUnitRepository.findByFullIdentifierAndActionUnit("test", actionUnit))
                .thenReturn(List.of());

        assertFalse(recordingUnitService.fullIdentifierAlreadyExistInAction(recordingUnit1));
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
    void save_ArkEntity_returnsSavedRecordingUnit() {
        // Arrange
        RecordingUnit arkRecordingUnit = new RecordingUnit();
        arkRecordingUnit.setId(1L);
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenReturn(arkRecordingUnit);

        // Act
        ArkEntity result = recordingUnitService.save(arkRecordingUnit);

        // Assert
        assertNotNull(result);
        assertEquals(arkRecordingUnit, result);
        verify(recordingUnitRepository, times(1)).save(arkRecordingUnit);
    }

    @Test
    void countByInstitution_returnsCorrectCount() {
        // Arrange
        Institution institution = new Institution();
        institution.setId(1L);
        when(recordingUnitRepository.countByCreatedByInstitution(institution)).thenReturn(10L);

        // Act
        long count = recordingUnitService.countByInstitution(institution);

        // Assert
        assertEquals(10L, count);
        verify(recordingUnitRepository, times(1)).countByCreatedByInstitution(institution);
    }

    @Test
    void generatedNextIdentifier_UNIQUE_returnsUniqueId() {
        // Arrange
        actionUnit.setRecordingUnitIdentifierFormat("{NUM_UE}");
        when(recordingUnitIdCounterRepository.ruNextValUnique(actionUnit.getId())).thenReturn(100);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(actionUnit, null, null);

        // Assert
        assertEquals(100, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValUnique(actionUnit.getId());
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }

    @Test
    void generatedNextIdentifier_PARENT_withNullParent_returnsUniqueId() {
        // Arrange
        actionUnit.setRecordingUnitIdentifierFormat("{NUM_PARENT}-{NUM_UE}");
        when(recordingUnitIdCounterRepository.ruNextValUnique(actionUnit.getId())).thenReturn(101);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(actionUnit, null, null);

        // Assert
        assertEquals(101, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValUnique(actionUnit.getId());
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }

    @Test
    void generatedNextIdentifier_PARENT_withParent_returnsParentId() {
        // Arrange
        actionUnit.setRecordingUnitIdentifierFormat("{NUM_PARENT}-{NUM_UE}");
        RecordingUnit parentRu = new RecordingUnit();
        parentRu.setId(5L);
        when(recordingUnitIdCounterRepository.ruNextValParent(parentRu.getId())).thenReturn(102);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(actionUnit, null, parentRu);

        // Assert
        assertEquals(102, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValParent(parentRu.getId());
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }

    @Test
    void generatedNextIdentifier_TYPE_UNIQUE_returnsTypeId() {
        // Arrange
        actionUnit.setRecordingUnitIdentifierFormat("{TYPE_UE}-{NUM_UE}");
        Concept unitType = new Concept();
        unitType.setId(10L);
        when(recordingUnitIdCounterRepository.ruNextValTypeUnique(actionUnit.getId(), unitType.getId())).thenReturn(103);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(actionUnit, unitType, null);

        // Assert
        assertEquals(103, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValTypeUnique(actionUnit.getId(), unitType.getId());
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }

    @Test
    void generatedNextIdentifier_TYPE_UNIQUE_withNullType_returnsTypeId() {
        // Arrange
        actionUnit.setRecordingUnitIdentifierFormat("{TYPE_UE}-{NUM_UE}");
        when(recordingUnitIdCounterRepository.ruNextValTypeUnique(actionUnit.getId(), null)).thenReturn(104);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(actionUnit, null, null);

        // Assert
        assertEquals(104, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValTypeUnique(actionUnit.getId(), null);
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }

    @Test
    void generatedNextIdentifier_PARENT_TYPE_withNullParent_returnsUniqueId() {
        // Arrange
        actionUnit.setRecordingUnitIdentifierFormat("{TYPE_UE}{NUM_PARENT}-{NUM_UE}");
        Concept unitType = new Concept();
        unitType.setId(10L);
        when(recordingUnitIdCounterRepository.ruNextValUnique(actionUnit.getId())).thenReturn(105);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(actionUnit, unitType, null);

        // Assert
        assertEquals(105, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValUnique(actionUnit.getId());
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }

    @Test
    void generatedNextIdentifier_PARENT_TYPE_withParentAndType_returnsParentTypeId() {
        // Arrange
        actionUnit.setRecordingUnitIdentifierFormat("{TYPE_UE}{NUM_PARENT}-{NUM_UE}");
        RecordingUnit parentRu = new RecordingUnit();
        parentRu.setId(5L);
        Concept unitType = new Concept();
        unitType.setId(10L);
        when(recordingUnitIdCounterRepository.ruNextValTypeParent(parentRu.getId(), unitType.getId())).thenReturn(106);

        // Act
        int result = recordingUnitService.generatedNextIdentifier(actionUnit, unitType, parentRu);

        // Assert
        assertEquals(106, result);
        verify(recordingUnitIdCounterRepository, times(1)).ruNextValTypeParent(parentRu.getId(), unitType.getId());
        verifyNoMoreInteractions(recordingUnitIdCounterRepository);
    }

    @Test
    void save_shouldSucceed_whenFormResponseIsNull() {
        // Arrange
        recordingUnitToSave.setFormResponse(null); // Explicitly set to null

        when(recordingUnitRepository.save(any(RecordingUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(conceptService.saveOrGetConcept(any(Concept.class))).thenReturn(new Concept());
        when(personRepository.findAllById(anyList())).thenReturn(List.of());

        // Act
        RecordingUnit result = recordingUnitService.save(recordingUnitToSave, new Concept());

        // Assert
        assertNotNull(result);
        assertNull(result.getFormResponse()); // The managed unit should have a null form response
        verify(customFormResponseService, never()).saveFormResponse(any(), any());
        // It will be saved twice, once in setupAdditionalAnswers and once at the end.
        verify(recordingUnitRepository, times(2)).save(any(RecordingUnit.class));
    }

    @Test
    void save_shouldThrowException_whenParentNotFound() {
        // Arrange
        RecordingUnit parentRef = new RecordingUnit();
        parentRef.setId(999L);
        recordingUnitToSave.getParents().add(parentRef);

        when(recordingUnitRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        FailedRecordingUnitSaveException exception = assertThrows(
                FailedRecordingUnitSaveException.class,
                () -> recordingUnitService.save(recordingUnitToSave, new Concept())
        );

        assertEquals("Parent not found: 999", exception.getMessage());
    }

    @Test
    void save_shouldSetContributors() {
        // Arrange
        Person contributor1 = new Person();
        contributor1.setId(101L);
        Person contributor2 = new Person();
        contributor2.setId(102L);
        recordingUnitToSave.getContributors().add(contributor1);
        recordingUnitToSave.getContributors().add(contributor2);

        List<Long> contributorIds = List.of(101L, 102L);
        List<Person> foundContributors = List.of(contributor1, contributor2);

        when(personRepository.findAllById(contributorIds)).thenReturn(foundContributors);
        when(recordingUnitRepository.save(any(RecordingUnit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conceptService.saveOrGetConcept(any(Concept.class))).thenReturn(new Concept());

        // Act
        RecordingUnit result = recordingUnitService.save(recordingUnitToSave, new Concept());

        // Assert
        assertNotNull(result);
        assertTrue(result.getContributors().containsAll(foundContributors));
        verify(personRepository).findAllById(contributorIds);
    }

    @Test
    void fullIdentifierAlreadyExistInAction_returnTrue_whenUnitIsNewAndIdentifierExists() {
        // Arrange
        RecordingUnit newUnit = new RecordingUnit(); // ID is null
        newUnit.setActionUnit(actionUnit);
        newUnit.setFullIdentifier("test");

        RecordingUnit existingUnit = new RecordingUnit();
        existingUnit.setId(2L);
        existingUnit.setActionUnit(actionUnit);
        existingUnit.setFullIdentifier("test");

        when(recordingUnitRepository.findByFullIdentifierAndActionUnit("test", actionUnit))
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
        RecordingUnit recordingUnit = new RecordingUnit();
        recordingUnit.setId(1L);
        recordingUnit.setActionUnit(actionUnit);
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
        assertSame(actionUnit, result.getActionUnit());
        assertSame(parentUnit, result.getParent());
        assertSame(parentType, result.getRuParentType());
        verify(recordingUnitIdInfoRepository).save(result);
    }

    @Nested
    @DisplayName("generateFullIdentifier tests")
    class GenerateFullIdentifierTest {

        @BeforeEach
        void setUp() {
            recordingUnitToSave.setId(99L); // Assume unit is saved and has an ID
            when(recordingUnitIdInfoRepository.save(any(RecordingUnitIdInfo.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("should return numerical id when format is null")
        void generateFullIdentifier_withNullFormat_shouldReturnNumericalId() {
            // Arrange
            actionUnit.setRecordingUnitIdentifierFormat(null);
            when(recordingUnitIdInfoRepository.findById(recordingUnitToSave.getId())).thenReturn(Optional.empty());

            // Act
            String identifier = recordingUnitService.generateFullIdentifier(actionUnit, recordingUnitToSave);

            // Assert
            assertEquals("0", identifier);
        }

        @Test
        @DisplayName("should return formatted identifier when using resolvers")
        void generateFullIdentifier_withResolvers_shouldReturnFormattedIdentifier() {
            // Arrange
            RecordingUnitService spiedService = spy(recordingUnitService);
            actionUnit.setRecordingUnitIdentifierFormat("{MOCK}-{NUM_UE:000}");

            RuIdentifierResolver mockResolver = mock(RuIdentifierResolver.class);
            when(mockResolver.formatUsesThisResolver(anyString())).thenAnswer(inv -> inv.getArgument(0, String.class).contains("{MOCK}"));
            when(mockResolver.resolve(anyString(), any(RecordingUnitIdInfo.class))).thenAnswer(inv -> inv.getArgument(0, String.class).replace("{MOCK}", "RESOLVED"));

            RuNumResolver numResolver = new RuNumResolver();

            Map<String, RuIdentifierResolver> resolvers = new LinkedHashMap<>();
            resolvers.put("MOCK", mockResolver);
            resolvers.put("NUM_UE", numResolver);
            doReturn(resolvers).when(spiedService).findAllIdentifierResolver();


            when(recordingUnitIdCounterRepository.ruNextValUnique(actionUnit.getId())).thenReturn(42);
            when(recordingUnitIdInfoRepository.findById(recordingUnitToSave.getId())).thenReturn(Optional.empty());

            // Act
            String identifier = spiedService.generateFullIdentifier(actionUnit, recordingUnitToSave);

            // Assert
            assertEquals("RESOLVED-042", identifier);
            verify(spiedService).findAllIdentifierResolver();
        }

        @Test
        @DisplayName("should use parent info when parent is present")
        void generateFullIdentifier_withParent_shouldUseParentForIdGeneration() {
            // Arrange
            RecordingUnitService spiedService = spy(recordingUnitService);
            RecordingUnit parentRu = new RecordingUnit();
            parentRu.setId(5L);
            recordingUnitToSave.getParents().add(parentRu);
            actionUnit.setRecordingUnitIdentifierFormat("{NUM_PARENT}-{NUM_UE}");

            RuNumResolver numResolver = new RuNumResolver();
            RuNumParentResolver numParentResolver = new RuNumParentResolver(recordingUnitIdInfoRepository);
            Map<String, RuIdentifierResolver> resolvers = new LinkedHashMap<>();
            resolvers.put("NUM_PARENT", numParentResolver);
            resolvers.put("NUM_UE", numResolver);
            doReturn(resolvers).when(spiedService).findAllIdentifierResolver();
            when(recordingUnitIdCounterRepository.ruNextValParent(parentRu.getId())).thenReturn(7);
            when(recordingUnitIdInfoRepository.findById(recordingUnitToSave.getId())).thenReturn(Optional.empty());
            RecordingUnitIdInfo parentInfo = new RecordingUnitIdInfo();
            parentInfo.setRuNumber(99);
            when(recordingUnitIdInfoRepository.findById(parentRu.getId())).thenReturn(Optional.of(parentInfo));
            // Act
            String identifier = spiedService.generateFullIdentifier(actionUnit, recordingUnitToSave);
            // Assert
            assertEquals("99-7", identifier);
            verify(recordingUnitIdCounterRepository).ruNextValParent(parentRu.getId());
            verify(spiedService).createOrGetInfoOf(recordingUnitToSave, parentRu);
            verify(recordingUnitIdInfoRepository).findById(parentRu.getId());
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

}