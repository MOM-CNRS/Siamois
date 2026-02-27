package fr.siamois.domain.services.actionUnit;


import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.exceptions.actionunit.NullActionUnitIdentifierException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.authorization.PermissionServiceImpl;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionUnitServiceTest {

    @Mock
    private ActionUnitRepository actionUnitRepository;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ActionCodeRepository actionCodeRepository;
    @Mock
    private PermissionServiceImpl permissionService;
    @Mock private InstitutionService institutionService;
    @Mock private TeamMemberRepository teamMemberRepository;


    @Spy
    @InjectMocks
    private ActionUnitService actionUnitService;

    @Mock private ActionUnit action;
    @Mock private Institution institution;
    @Mock private UserInfo userInfo;
    @Mock private Person person;


    SpatialUnit spatialUnit1;
    ActionUnit actionUnit1;
    ActionUnit actionUnit2;

    ActionUnit actionUnitWithCodes;
    ActionCode primaryActionCode;
    ActionCode secondaryActionCode1;
    ActionCode secondaryActionCode2;
    ActionCode failedCode;
    ActionCode secondaryActionCodeThatWillBeRemoved;
    Concept c1, c2, c3;

    UserInfo info;

    @BeforeEach
    void setUp() {

        Person p =new Person();
        Institution i = new Institution();
        i.setIdentifier("MOM");
        info = new UserInfo(i,p,"fr");
        spatialUnit1 = new SpatialUnit();
        actionUnit1 = new ActionUnit();
        actionUnit2 = new ActionUnit();
        spatialUnit1.setId(1L);
        actionUnit1.setId(1L);
        actionUnit2.setId(2L);

        c1 = new Concept();
        c2 = new Concept();
        c3 = new Concept();

        // For action codes test
        c1.setExternalId("1");
        c2.setExternalId("2");
        c3.setExternalId("3");

        actionUnitWithCodes = new ActionUnit();

        primaryActionCode = new ActionCode();
        primaryActionCode.setCode("primary");
        primaryActionCode.setType(c1);

        secondaryActionCode1 = new ActionCode();
        secondaryActionCode1.setCode("secondary1");
        secondaryActionCode1.setType(c2);

        secondaryActionCode2 = new ActionCode();
        secondaryActionCode2.setCode("secondary2");
        secondaryActionCode2.setType(c3);

        secondaryActionCodeThatWillBeRemoved = new ActionCode();
        secondaryActionCodeThatWillBeRemoved.setCode("willberemoved");

        actionUnitWithCodes.setPrimaryActionCode(primaryActionCode);
        actionUnitWithCodes.setSecondaryActionCodes(new HashSet<>(List.of(secondaryActionCode1, secondaryActionCode2, secondaryActionCodeThatWillBeRemoved)));

        actionUnitWithCodes.setIdentifier("Test");
        Institution institution1 = new Institution();
        institution.setIdentifier("MOM");
        actionUnitWithCodes.setCreatedByInstitution(institution1);

        failedCode = new ActionCode();
        failedCode.setType(c2);
        failedCode.setCode("primary");



    }


    @Test
    void findById_success() {

        when(actionUnitRepository.findById(actionUnit1.getId())).thenReturn(Optional.ofNullable(actionUnit1));

        // act
        ActionUnit actualResult = actionUnitService.findById(spatialUnit1.getId());

        // assert
        assertEquals(actionUnit1, actualResult);
    }

    @Test
    void findById_Exception() {

        when(actionUnitRepository.findById(actionUnit1.getId())).thenReturn(Optional.empty());


        // Act & Assert
        Exception exception = assertThrows(
                Exception.class,
                () -> actionUnitService.findById(spatialUnit1.getId())
        );

        assertEquals("ActionUnit not found with ID: 1", exception.getMessage());

    }

    @Test
    void SaveWithActionCodes_Success() throws ActionUnitAlreadyExistsException {

        lenient().when(conceptService.saveOrGetConcept(c1)).thenReturn(c1);
        lenient().when(conceptService.saveOrGetConcept(c2)).thenReturn(c2);
        lenient().when(conceptService.saveOrGetConcept(c3)).thenReturn(c3);

        lenient().when(actionCodeRepository.findById(primaryActionCode.getCode())).thenReturn(Optional.ofNullable(primaryActionCode));
        lenient().when(actionCodeRepository.findById(secondaryActionCode1.getCode())).thenReturn(Optional.ofNullable(secondaryActionCode1));
        lenient().when(actionCodeRepository.findById(secondaryActionCode2.getCode())).thenReturn(Optional.empty()); // It means this code is not in DB

        when(actionUnitRepository.save(actionUnitWithCodes)).thenReturn(actionUnitWithCodes);
        when(actionUnitRepository.findById(actionUnitWithCodes.getId())).thenReturn(Optional.ofNullable(actionUnitWithCodes));

        ActionUnit result = actionUnitService.save(actionUnitWithCodes, List.of(secondaryActionCode1, secondaryActionCode2),info);
        // assert
        assertNotNull(result);
        assertEquals(primaryActionCode, result.getPrimaryActionCode());
        assertEquals(new HashSet<>(List.of(secondaryActionCode1, secondaryActionCode2)), result.getSecondaryActionCodes());
        assertEquals("MOM-Test", result.getFullIdentifier());
    }

    @Test
    void Save_FailureBecauseIdentifierIsMissing() {

        lenient().when(conceptService.saveOrGetConcept(c1)).thenReturn(c1);
        lenient().when(conceptService.saveOrGetConcept(c2)).thenReturn(c2);
        lenient().when(conceptService.saveOrGetConcept(c3)).thenReturn(c3);

        lenient().when(actionCodeRepository.findById(primaryActionCode.getCode())).thenReturn(Optional.ofNullable(primaryActionCode));
        lenient().when(actionCodeRepository.findById(secondaryActionCode1.getCode())).thenReturn(Optional.ofNullable(secondaryActionCode1));
        lenient().when(actionCodeRepository.findById(secondaryActionCode2.getCode())).thenReturn(Optional.empty()); // It means this code is not in DB

        when(actionUnitRepository.findById(actionUnitWithCodes.getId())).thenReturn(Optional.ofNullable(actionUnitWithCodes));

        actionUnitWithCodes.setIdentifier(null); // remove identifier

        List<ActionCode> toSave = List.of(secondaryActionCode1, secondaryActionCode2);

        // Act & Assert
        Exception exception = assertThrows(
                NullActionUnitIdentifierException.class,
                () -> actionUnitService.save(actionUnitWithCodes, toSave,info)
        );

        assertEquals("ActionUnit identifier must be set", exception.getMessage());
    }

    @Test
    void SaveNotTransactional_FailureBecauseSameNameExists() {


        Optional<ActionUnit> opt = Optional.ofNullable(actionUnit1);
        assert actionUnit1 != null;
        actionUnit1.setName("already exists");
        actionUnit1.setCreatedByInstitution(new Institution());

        when(actionUnitRepository.findByNameAndCreatedByInstitution(any(String.class),
                any(Institution.class))).thenReturn(opt);

        // Act & Assert
        Exception exception = assertThrows(
                ActionUnitAlreadyExistsException.class,
                () -> actionUnitService.saveNotTransactional(info, actionUnit1, new Concept())
        );

        assertEquals("Action unit with name already exists already exist in institution null", exception.getMessage());
    }

    @Test
    void SaveNotTransactional_FailureBecauseSameIdentifierExists() {


        Optional<ActionUnit> opt = Optional.ofNullable(actionUnit1);
        assert actionUnit1 != null;
        actionUnit1.setIdentifier("already-exists");
        actionUnit1.setCreatedByInstitution(new Institution());

        when(actionUnitRepository.findByIdentifierAndCreatedByInstitution(any(String.class),
                any(Institution.class))).thenReturn(opt);

        // Act & Assert
        Exception exception = assertThrows(
                ActionUnitAlreadyExistsException.class,
                () -> actionUnitService.saveNotTransactional(info, actionUnit1, new Concept())
        );

        assertEquals("Action unit with identifier already-exists already exist in institution null", exception.getMessage());
    }

    @Test
    void SaveActionCodes_FailedCodeExistsButTypeDoesNotMatch() {
        lenient().when(conceptService.saveOrGetConcept(c1)).thenReturn(c1);
        lenient().when(conceptService.saveOrGetConcept(c2)).thenReturn(c2);

        lenient().when(actionCodeRepository.findById(failedCode.getCode())).thenReturn(Optional.ofNullable(primaryActionCode));


        actionUnitWithCodes.setPrimaryActionCode(failedCode);

        List<ActionCode> toSave = List.of(secondaryActionCode1, secondaryActionCode2);

        // Act & Assert
        Exception exception = assertThrows(
                FailedActionUnitSaveException.class,
                () -> actionUnitService.save(actionUnitWithCodes, toSave,info)
        );

        assertEquals("Code exists but type does not match", exception.getMessage());
    }

    @Test
    void SaveActionCodes_Exception() {
        lenient().when(conceptService.saveOrGetConcept(c1)).thenReturn(c1);
        lenient().when(conceptService.saveOrGetConcept(c2)).thenReturn(c2);
        lenient().when(conceptService.saveOrGetConcept(c3)).thenReturn(c3);
        lenient().when(actionCodeRepository.findById(primaryActionCode.getCode())).thenReturn(Optional.ofNullable(primaryActionCode));
        lenient().when(actionCodeRepository.findById(secondaryActionCode1.getCode())).thenReturn(Optional.ofNullable(secondaryActionCode1));
        lenient().when(actionCodeRepository.findById(secondaryActionCode2.getCode())).thenReturn(Optional.empty());
        when(actionUnitRepository.save(actionUnitWithCodes)).thenThrow(new RuntimeException("Database error"));
        when(actionUnitRepository.findById(actionUnitWithCodes.getId())).thenReturn(Optional.ofNullable(actionUnitWithCodes));

        List<ActionCode> toSave = List.of(secondaryActionCode1, secondaryActionCode2);

        // Act & Assert
        Exception exception = assertThrows(
                FailedActionUnitSaveException.class,
                () -> actionUnitService.save(actionUnitWithCodes, toSave,info)
        );
        assertEquals("Database error", exception.getMessage());

    }

    @Test
    void findByArk() {
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(1L);

        Ark ark = new Ark();
        ark.setInternalId(1L);

        when(actionUnitRepository.findByArk(ark)).thenReturn(Optional.of(actionUnit));

        Optional<ActionUnit> result = actionUnitRepository.findByArk(ark);

        assertThat(result)
                .isPresent()
                .contains(actionUnit);
    }

    @Test
    void findWithoutArk() {
        Institution institution1 = new Institution();
        institution1.setId(1L);

        ActionUnit actionUnitLocal = new ActionUnit();
        actionUnitLocal.setId(1L);
        ActionUnit actionUnitLocal2 = new ActionUnit();
        actionUnitLocal2.setId(2L);

        when(actionUnitRepository.findAllByArkIsNullAndCreatedByInstitution(institution1)).thenReturn(List.of(actionUnitLocal, actionUnitLocal2));

        List<ActionUnit> result = actionUnitService.findWithoutArk(institution1);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(actionUnitLocal));
        assertTrue(result.contains(actionUnitLocal2));
    }

    @Test
    void save() {
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(1L);

        when(actionUnitRepository.save(actionUnit)).thenReturn(actionUnit);

        ArkEntity result = actionUnitService.save(actionUnit);

        assertNotNull(result);
        assertEquals(actionUnit, result);
    }

    @Test
    void testFindByArk() {
        Ark ark = new Ark();
        ark.setInternalId(12L);
        ark.setQualifier("UBDQSD");

        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(1L);
        actionUnit.setArk(ark);

        when(actionUnitRepository.findByArk(any(Ark.class))).thenReturn(Optional.of(actionUnit));

        Optional<ActionUnit> result = actionUnitService.findByArk(ark);

        assertTrue(result.isPresent());
    }

    @Test
    void countByInstitution_success() {
        when(actionUnitRepository.countByCreatedByInstitution(any(Institution.class))).thenReturn(3L);
        assertEquals(3,actionUnitService.countByInstitution(new Institution()));
    }

    @Test
    void countBySpatialContext_success() {
        SpatialUnit spatialUnit = new SpatialUnit();
        spatialUnit.setId(1L);

        when(actionUnitRepository.countBySpatialContext(spatialUnit.getId())).thenReturn(5);

        long result = actionUnitService.countBySpatialContext(spatialUnit);

        assertEquals(5L, result);
    }

    @Test
    void findAllByInstitution_success() {
        Institution institution1 = new Institution();
        institution1.setId(1L);

        actionUnit1 = new ActionUnit();
        actionUnit1.setId(1L);
        actionUnit1.setFullIdentifier("AU-1");
        actionUnit2 = new ActionUnit();
        actionUnit2.setId(2L);
        actionUnit2.setFullIdentifier("AU-2");

        when(actionUnitRepository.findByCreatedByInstitution(institution1)).thenReturn(new HashSet<>(List.of(actionUnit1, actionUnit2)));

        Set<ActionUnit> result = actionUnitService.findAllByInstitution(institution1);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(actionUnit1));
        assertTrue(result.contains(actionUnit2));
    }


    @Test
    void isActionUnitStillOngoing_returnsFalseWhenBeginIsNull() {
        ActionUnit au = new ActionUnit();
        au.setEndDate(OffsetDateTime.now().plusDays(1));
        assertFalse(actionUnitService.isActionUnitStillOngoing(au));
    }

    @Test
    void isActionUnitStillOngoing_returnsTrueWhenEndIsNull() {
        ActionUnit au = new ActionUnit();
        au.setBeginDate(OffsetDateTime.now().minusDays(1));
        assertTrue(actionUnitService.isActionUnitStillOngoing(au));
    }

    @Test
    void isActionUnitStillOngoing_returnsFalseWhenNowBeforeBegin() {
        ActionUnit au = new ActionUnit();
        OffsetDateTime begin = OffsetDateTime.now().plusHours(2);
        au.setBeginDate(begin);
        au.setEndDate(begin.plusHours(1));
        assertFalse(actionUnitService.isActionUnitStillOngoing(au));
    }

    @Test
    void isActionUnitStillOngoing_returnsTrueWhenNowWithinRange() {
        OffsetDateTime begin = OffsetDateTime.now().minusHours(1);
        OffsetDateTime end = OffsetDateTime.now().plusHours(1);
        ActionUnit au = new ActionUnit();
        au.setBeginDate(begin);
        au.setEndDate(end);
        assertTrue(actionUnitService.isActionUnitStillOngoing(au));
    }

    @Test
    void isActionUnitStillOngoing_returnsFalseWhenNowAfterEnd() {
        OffsetDateTime end = OffsetDateTime.now().minusMinutes(1);
        OffsetDateTime begin = end.minusHours(1);
        ActionUnit au = new ActionUnit();
        au.setBeginDate(begin);
        au.setEndDate(end);
        assertFalse(actionUnitService.isActionUnitStillOngoing(au));
    }



    private void commonStubs() {
        when(action.getCreatedByInstitution()).thenReturn(institution);
        when(userInfo.getUser()).thenReturn(person);
    }

    @Test
    void returnsTrue_whenUserIsInstitutionManager() {
        commonStubs();
        when(institutionService.isManagerOf(institution, person)).thenReturn(true);

        assertTrue(actionUnitService.canCreateRecordingUnit(userInfo, action));

    }

    @Test
    void returnsTrue_whenUserIsActionUnitManager() {
        commonStubs();
        when(institutionService.isManagerOf(institution, person)).thenReturn(false);
        doReturn(true).when(actionUnitService).isManagerOf(action, person);

        assertTrue(actionUnitService.canCreateRecordingUnit(userInfo, action));

    }

    @Test
    void returnsTrue_whenUserIsTeamMember_andActionIsOngoing() {
        commonStubs();
        when(institutionService.isManagerOf(institution, person)).thenReturn(false);
        doReturn(false).when(actionUnitService).isManagerOf(action, person);
        when(teamMemberRepository.existsByActionUnitIdAndPerson(action, person)).thenReturn(true);
        doReturn(true).when(actionUnitService).isActionUnitStillOngoing(action);

        assertTrue(actionUnitService.canCreateRecordingUnit(userInfo, action));

        verify(teamMemberRepository).existsByActionUnitIdAndPerson(action, person);
        verify(actionUnitService).isActionUnitStillOngoing(action);
    }

    @Test
    void returnsFalse_whenUserIsTeamMember_butActionIsClosed() {
        commonStubs();
        when(institutionService.isManagerOf(institution, person)).thenReturn(false);
        doReturn(false).when(actionUnitService).isManagerOf(action, person);
        when(teamMemberRepository.existsByActionUnitIdAndPerson(action, person)).thenReturn(true);
        doReturn(false).when(actionUnitService).isActionUnitStillOngoing(action);

        assertFalse(actionUnitService.canCreateRecordingUnit(userInfo, action));
    }

    @Test
    void returnsFalse_whenUserHasNoRole() {
        commonStubs();
        when(institutionService.isManagerOf(institution, person)).thenReturn(false);
        doReturn(false).when(actionUnitService).isManagerOf(action, person);
        when(teamMemberRepository.existsByActionUnitIdAndPerson(action, person)).thenReturn(false);
        // `isActionUnitStillOngoing` won’t be called because teamMemberRepository returns false

        assertFalse(actionUnitService.canCreateRecordingUnit(userInfo, action));
        verify(actionUnitService, never()).isActionUnitStillOngoing(any());
    }

    @Test
    void findChildrenByParentAndInstitution_returnsInitializedActionUnits() {
        // Arrange
        Long parentId = 1L;
        Long institutionId = 1L;
        List<ActionUnit> mockActionUnits = List.of(mock(ActionUnit.class), mock(ActionUnit.class));
        when(actionUnitRepository.findChildrenByParentAndInstitution(parentId, institutionId)).thenReturn(mockActionUnits);

        // Act
        List<ActionUnit> result = actionUnitService.findChildrenByParentAndInstitution(parentId, institutionId);

        // Assert
        assertEquals(2, result.size());
        verify(actionUnitRepository).findChildrenByParentAndInstitution(parentId, institutionId);
    }

    @Test
    void findAllWithoutParentsByInstitution_returnsInitializedActionUnits() {
        // Arrange
        Long institutionId = 1L;
        List<ActionUnit> mockActionUnits = List.of(mock(ActionUnit.class), mock(ActionUnit.class));
        when(actionUnitRepository.findRootsByInstitution(institutionId)).thenReturn(mockActionUnits);

        // Act
        List<ActionUnit> result = actionUnitService.findAllWithoutParentsByInstitution(institutionId);

        // Assert
        assertEquals(2, result.size());
        verify(actionUnitRepository).findRootsByInstitution(institutionId);
    }

    @Test
    void findBySpatialContextAndInstitution_returnsInitializedActionUnits() {
        // Arrange
        Long spatialId = 1L;
        List<ActionUnit> mockActionUnits = List.of(mock(ActionUnit.class), mock(ActionUnit.class));
        when(actionUnitRepository.findBySpatialContext(spatialId)).thenReturn(mockActionUnits);

        // Act
        List<ActionUnit> result = actionUnitService.findBySpatialContext(spatialId);

        // Assert
        assertEquals(2, result.size());
        verify(actionUnitRepository).findBySpatialContext(spatialId);
    }

    @Test
    void existsRootChildrenByRelatedSpatialUnit_ShouldReturnTrue_WhenChildrenExist() {
        // Arrange
        Long spatialUnitId = 1L;
        when(actionUnitRepository.existsRootChildrenByRelatedSpatialUnit(spatialUnitId))
                .thenReturn(true);

        // Act
        boolean result = actionUnitService.existsRootChildrenByRelatedSpatialUnit(spatialUnitId);

        // Assert
        assertTrue(result, "La méthode doit retourner true si des enfants existent pour l'unité spatiale donnée.");
    }




}