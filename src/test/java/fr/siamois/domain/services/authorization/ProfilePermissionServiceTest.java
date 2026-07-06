package fr.siamois.domain.services.authorization;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfilePermissionServiceTest {

    private static final String CODE = PermissionConstants.ORGANIZATION_CREATE_PLACES;

    @Mock
    private PersonProfileAssignmentRepository assignmentRepository;

    @InjectMocks
    private ProfilePermissionService service;

    private PersonDTO person;
    private InstitutionDTO institution;
    private UserInfo userInfo;

    @BeforeEach
    void setUp() {
        person = new PersonDTO();
        person.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(10L);
        userInfo = new UserInfo(institution, person, "fr");
    }

    @Test
    void hasInstancePermission_returnsFalse_whenPersonIsNull() {
        assertFalse(service.hasInstancePermission(null, CODE));
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void hasInstancePermission_delegatesToRepository() {
        when(assignmentRepository.personHasInstancePermission(1L, CODE)).thenReturn(true);

        assertTrue(service.hasInstancePermission(person, CODE));
    }

    @Test
    void hasOrganizationPermission_returnsTrue_whenInstanceProfileGrantsIt() {
        when(assignmentRepository.personHasInstancePermission(1L, CODE)).thenReturn(true);

        assertTrue(service.hasOrganizationPermission(userInfo, CODE));
        verify(assignmentRepository, never()).personHasPermissionInInstitution(anyLong(), anyLong(), anyString());
    }

    @Test
    void hasOrganizationPermission_checksInstitutionScopedProfiles() {
        when(assignmentRepository.personHasInstancePermission(1L, CODE)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, CODE)).thenReturn(true);

        assertTrue(service.hasOrganizationPermission(userInfo, CODE));
    }

    @Test
    void hasOrganizationPermission_returnsFalse_whenInstitutionIsNull() {
        when(assignmentRepository.personHasInstancePermission(1L, CODE)).thenReturn(false);

        assertFalse(service.hasOrganizationPermission(person, null, CODE));
        verify(assignmentRepository, never()).personHasPermissionInInstitution(anyLong(), anyLong(), anyString());
    }

    @Test
    void hasProjectPermission_returnsTrue_whenOrganizationProfileGrantsIt() {
        when(assignmentRepository.personHasInstancePermission(1L, CODE)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, CODE)).thenReturn(true);

        assertTrue(service.hasProjectPermission(userInfo, 5L, CODE));
        verify(assignmentRepository, never()).personHasPermissionInActionUnit(anyLong(), anyLong(), anyString());
    }

    @Test
    void hasProjectPermission_checksActionUnitScopedProfiles() {
        when(assignmentRepository.personHasInstancePermission(1L, CODE)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, CODE)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInActionUnit(1L, 5L, CODE)).thenReturn(true);

        assertTrue(service.hasProjectPermission(userInfo, 5L, CODE));
    }

    @Test
    void hasProjectPermission_returnsFalse_whenActionUnitIdIsNull() {
        when(assignmentRepository.personHasInstancePermission(1L, CODE)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, CODE)).thenReturn(false);

        assertFalse(service.hasProjectPermission(userInfo, null, CODE));
        verify(assignmentRepository, never()).personHasPermissionInActionUnit(anyLong(), anyLong(), anyString());
    }

    @Test
    void hasRecordingUnitWritePermission_usesTheRecordingUnitActionUnit() {
        ActionUnitSummaryDTO actionUnit = new ActionUnitSummaryDTO();
        actionUnit.setId(5L);
        RecordingUnitDTO recordingUnit = new RecordingUnitDTO();
        recordingUnit.setActionUnit(actionUnit);

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInActionUnit(1L, 5L, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)).thenReturn(true);

        assertTrue(service.hasRecordingUnitWritePermission(userInfo, recordingUnit));
    }

    @Test
    void hasRecordingUnitWritePermission_returnsFalse_whenRecordingUnitHasNoActionUnitAndNoWiderGrant() {
        RecordingUnitDTO recordingUnit = new RecordingUnitDTO();

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)).thenReturn(false);

        assertFalse(service.hasRecordingUnitWritePermission(userInfo, recordingUnit));
        verify(assignmentRepository, never()).personHasPermissionInActionUnit(anyLong(), anyLong(), anyString());
    }
}
