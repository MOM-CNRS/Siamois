package fr.siamois.domain.services.authorization.writeverifier;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingUnitWriteVerifierTest {

    @Mock private InstitutionService institutionService;
    @Mock private ActionUnitService actionUnitService;
    @Mock private TeamMemberRepository teamMemberRepository;

    @Mock
    private ActionUnitWriteVerifier actionUnitWriteVerifier;

    @InjectMocks
    private RecordingUnitWriteVerifier handler;

    @Mock private UserInfo userInfo;
    @Mock private Person user; // or whatever type userInfo.getUser() returns

    @Mock private RecordingUnit recordingUnit;
    @Mock private ActionUnit actionUnit;
    @Mock private Institution institution;



    @Test
    void getEntityClass_shouldReturnRecordingUnitClass() {
        // Act
        Class<? extends TraceableEntity> result = handler.getEntityClass();

        // Assert
        assertEquals(RecordingUnit.class, result);
    }

    @BeforeEach
    void setUp() {
        user = new Person();
        user.setUsername("testUser");
        user.setId(1L);

        institution = new Institution();
        institution.setId(1L);
        institution.setName("Test Institution");

        userInfo = new UserInfo(institution, user, "fr");
    }

    @Test
    void returnsFalse_whenActionUnitIsNull() {
        when(recordingUnit.getActionUnit()).thenReturn(null);

        boolean result = handler.hasSpecificWritePermission(userInfo, recordingUnit);

        assertFalse(result);
        verifyNoInteractions(institutionService, actionUnitService, teamMemberRepository);
    }

    @Test
    void returnsTrue_whenUserIsManagerOfCreatedByInstitution() {
        when(recordingUnit.getActionUnit()).thenReturn(actionUnit);
        when(actionUnit.getCreatedByInstitution()).thenReturn(institution);

        when(institutionService.isManagerOf(institution, user)).thenReturn(true);

        boolean result = handler.hasSpecificWritePermission(userInfo, recordingUnit);

        assertTrue(result);

        // optional: ensure short-circuit means later checks needn't be called
        verify(institutionService).isManagerOf(institution, user);
        verify(actionUnitService, never()).isManagerOf(any(), any());
        verify(teamMemberRepository, never()).existsByActionUnitAndPerson(any(), any());
        verify(actionUnitService, never()).isActionUnitStillOngoing(any());
    }

    @Test
    void returnsTrue_whenUserIsManagerOfActionUnit() {
        when(recordingUnit.getActionUnit()).thenReturn(actionUnit);
        when(actionUnit.getCreatedByInstitution()).thenReturn(institution);

        when(institutionService.isManagerOf(institution, user)).thenReturn(false);
        when(actionUnitService.isManagerOf(actionUnit, user)).thenReturn(true);

        boolean result = handler.hasSpecificWritePermission(userInfo, recordingUnit);

        assertTrue(result);

        verify(institutionService).isManagerOf(institution, user);
        verify(actionUnitService).isManagerOf(actionUnit, user);
        verify(teamMemberRepository, never()).existsByActionUnitAndPerson(any(), any());
        verify(actionUnitService, never()).isActionUnitStillOngoing(any());
    }

    @Test
    void returnsTrue_whenUserIsTeamMember_andActionUnitIsStillOngoing() {
        when(recordingUnit.getActionUnit()).thenReturn(actionUnit);
        when(actionUnit.getCreatedByInstitution()).thenReturn(institution);

        when(institutionService.isManagerOf(institution, user)).thenReturn(false);
        when(actionUnitService.isManagerOf(actionUnit, user)).thenReturn(false);

        when(teamMemberRepository.existsByActionUnitAndPerson(actionUnit, user)).thenReturn(true);
        when(actionUnitService.isActionUnitStillOngoing(actionUnit)).thenReturn(true);

        boolean result = handler.hasSpecificWritePermission(userInfo, recordingUnit);

        assertTrue(result);

        verify(teamMemberRepository).existsByActionUnitAndPerson(actionUnit, user);
        verify(actionUnitService).isActionUnitStillOngoing(actionUnit);
    }

    @Test
    void returnsFalse_whenUserIsTeamMember_butActionUnitIsNotOngoing() {
        when(recordingUnit.getActionUnit()).thenReturn(actionUnit);
        when(actionUnit.getCreatedByInstitution()).thenReturn(institution);

        when(institutionService.isManagerOf(institution, user)).thenReturn(false);
        when(actionUnitService.isManagerOf(actionUnit, user)).thenReturn(false);

        when(teamMemberRepository.existsByActionUnitAndPerson(actionUnit, user)).thenReturn(true);
        when(actionUnitService.isActionUnitStillOngoing(actionUnit)).thenReturn(false);

        boolean result = handler.hasSpecificWritePermission(userInfo, recordingUnit);

        assertFalse(result);
    }





}