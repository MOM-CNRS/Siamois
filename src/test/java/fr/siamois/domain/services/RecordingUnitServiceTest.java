package fr.siamois.domain.services;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.auth.Person;
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
import fr.siamois.domain.services.recordingunit.identifier.generic.RuIdentifierResolver;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.infrastructure.api.dto.ConceptFieldDTO;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdCounterRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdInfoRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
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
        antRelationship.setType(StratigraphicRelationshipService.ASYNCHRONOUS);
        StratigraphicRelationship syncRelationship = new StratigraphicRelationship();
        syncRelationship.setUnit1(recordingUnitToSave);
        syncRelationship.setUnit2(synchronousUnit);
        syncRelationship.setType(StratigraphicRelationshipService.SYNCHRONOUS);
        StratigraphicRelationship postRelationship = new StratigraphicRelationship();
        postRelationship.setUnit1(posteriorUnit);
        postRelationship.setUnit2(recordingUnitToSave);
        postRelationship.setType(StratigraphicRelationshipService.ASYNCHRONOUS);

        Person p = new Person();

        Concept c = new Concept();
        when(conceptService.saveOrGetConcept(c)).thenReturn(c);


        when(recordingUnitRepository.save(any(RecordingUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(personRepository.findAllById(anyList())).thenReturn(List.of(p));

        when(recordingUnitRepository.findById(10L)).thenReturn(Optional.of(parent1Unit));

        RecordingUnitIdInfo info = new RecordingUnitIdInfo();

        RecordingUnit result = recordingUnitService.save(recordingUnitToSave,c,
                List.of(anteriorUnit),
                List.of(synchronousUnit),
                List.of(posteriorUnit)
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
        antRelationship.setType(StratigraphicRelationshipService.ASYNCHRONOUS);
        StratigraphicRelationship syncRelationship = new StratigraphicRelationship();
        syncRelationship.setUnit1(recordingUnitToSave);
        syncRelationship.setUnit2(synchronousUnit);
        syncRelationship.setType(StratigraphicRelationshipService.SYNCHRONOUS);
        StratigraphicRelationship postRelationship = new StratigraphicRelationship();
        postRelationship.setUnit1(posteriorUnit);
        postRelationship.setUnit2(recordingUnitToSave);
        postRelationship.setType(StratigraphicRelationshipService.ASYNCHRONOUS);

        Person p = new Person();

        Concept c = new Concept();
        when(conceptService.saveOrGetConcept(c)).thenReturn(c);

        when(personRepository.findAllById(anyList())).thenReturn(List.of(p));

        when(recordingUnitRepository.save(any(RecordingUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doNothing().when(customFormResponseService)
                .saveFormResponse(any(CustomFormResponse.class), any(CustomFormResponse.class));

        RecordingUnitIdInfo info = new RecordingUnitIdInfo();

        RecordingUnit res = recordingUnitService.save(recordingUnitToSave, c, List.of(anteriorUnit),
                List.of(synchronousUnit),
                List.of(posteriorUnit));
        assertNotNull(res);
        verify(customFormResponseService, times(1))
                .saveFormResponse(any(CustomFormResponse.class), any(CustomFormResponse.class));
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

    static class FakeResolver implements RuIdentifierResolver {

        @Override
        public @NonNull String getCode() {
            return "";
        }

        @Override
        public @Nullable String getDescriptionLanguageCode() {
            return "";
        }

        @Override
        public @NonNull String getTitleCode() {
            return "";
        }

        @Override
        public @NonNull String resolve(@NonNull String baseFormatString, @NonNull RecordingUnitIdInfo ruInfo) {
            return "";
        }
    }

}