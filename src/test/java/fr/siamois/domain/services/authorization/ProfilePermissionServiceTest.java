package fr.siamois.domain.services.authorization;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfilePermissionServiceTest {

    private static final String CODE = PermissionConstants.ORGANIZATION_MANAGE_PLACES;

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

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInActionUnit(1L, 5L, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)).thenReturn(true);

        assertTrue(service.hasRecordingUnitWritePermission(userInfo, recordingUnit));
    }

    @Test
    void hasRecordingUnitWritePermission_returnsFalse_whenRecordingUnitHasNoActionUnitAndNoWiderGrant() {
        RecordingUnitDTO recordingUnit = new RecordingUnitDTO();

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS)).thenReturn(false);

        assertFalse(service.hasRecordingUnitWritePermission(userInfo, recordingUnit));
        verify(assignmentRepository, never()).personHasPermissionInActionUnit(anyLong(), anyLong(), anyString());
    }

    @Test
    void hasSpecimenWritePermission_usesTheSpecimenActionUnit() {
        ActionUnitSummaryDTO actionUnit = new ActionUnitSummaryDTO();
        actionUnit.setId(5L);
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setActionUnit(actionUnit);

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_EDIT_FINDS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_EDIT_FINDS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_EDIT_FINDS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInActionUnit(1L, 5L, PermissionConstants.PROJECT_EDIT_FINDS)).thenReturn(true);

        assertTrue(service.hasSpecimenWritePermission(userInfo, specimen));
    }

    @Test
    void hasSpecimenWritePermission_returnsFalse_whenSpecimenHasNoActionUnitAndNoWiderGrant() {
        SpecimenDTO specimen = new SpecimenDTO();

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_EDIT_FINDS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_EDIT_FINDS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_EDIT_FINDS)).thenReturn(false);

        assertFalse(service.hasSpecimenWritePermission(userInfo, specimen));
        verify(assignmentRepository, never()).personHasPermissionInActionUnit(anyLong(), anyLong(), anyString());
    }

    @Test
    void hasPhaseWritePermission_usesThePhaseActionUnit() {
        ActionUnitSummaryDTO actionUnit = new ActionUnitSummaryDTO();
        actionUnit.setId(5L);
        PhaseDTO phase = new PhaseDTO();
        phase.setActionUnit(actionUnit);

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_EDIT_PHASES)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_EDIT_PHASES)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_EDIT_PHASES)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInActionUnit(1L, 5L, PermissionConstants.PROJECT_EDIT_PHASES)).thenReturn(true);

        assertTrue(service.hasPhaseWritePermission(userInfo, phase));
    }

    @Test
    void hasPhaseWritePermission_returnsFalse_whenPhaseHasNoActionUnitAndNoWiderGrant() {
        PhaseDTO phase = new PhaseDTO();

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_EDIT_PHASES)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_EDIT_PHASES)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_EDIT_PHASES)).thenReturn(false);

        assertFalse(service.hasPhaseWritePermission(userInfo, phase));
        verify(assignmentRepository, never()).personHasPermissionInActionUnit(anyLong(), anyLong(), anyString());
    }

    @Test
    void hasContainerWritePermission_usesTheContainerActionUnit() {
        ActionUnitSummaryDTO actionUnit = new ActionUnitSummaryDTO();
        actionUnit.setId(5L);
        ContainerDTO container = new ContainerDTO();
        container.setActionUnit(actionUnit);

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_EDIT_CONTAINERS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_EDIT_CONTAINERS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_EDIT_CONTAINERS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInActionUnit(1L, 5L, PermissionConstants.PROJECT_EDIT_CONTAINERS)).thenReturn(true);

        assertTrue(service.hasContainerWritePermission(userInfo, container));
    }

    @Test
    void hasContainerWritePermission_returnsFalse_whenContainerHasNoActionUnitAndNoWiderGrant() {
        ContainerDTO container = new ContainerDTO();

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_EDIT_CONTAINERS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_EDIT_CONTAINERS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_EDIT_CONTAINERS)).thenReturn(false);

        assertFalse(service.hasContainerWritePermission(userInfo, container));
        verify(assignmentRepository, never()).personHasPermissionInActionUnit(anyLong(), anyLong(), anyString());
    }

    @Test
    void hasActionUnitWritePermission_returnsTrue_whenOrganizationCanManageActions() {
        ActionUnitDTO actionUnit = new ActionUnitDTO();
        actionUnit.setId(5L);

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)).thenReturn(true);

        assertTrue(service.hasActionUnitWritePermission(userInfo, actionUnit));
        verify(assignmentRepository, never()).personHasPermissionInActionUnit(anyLong(), anyLong(), anyString());
    }

    @Test
    void hasActionUnitWritePermission_fallsBackToProjectManageSettings_whenNotOrganizationManager() {
        ActionUnitDTO actionUnit = new ActionUnitDTO();
        actionUnit.setId(5L);

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.PROJECT_MANAGE_SETTINGS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.PROJECT_MANAGE_SETTINGS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInActionUnit(1L, 5L, PermissionConstants.PROJECT_MANAGE_SETTINGS)).thenReturn(true);

        assertTrue(service.hasActionUnitWritePermission(userInfo, actionUnit));
    }

    @Test
    void hasActionUnitWritePermission_returnsFalse_whenActionUnitIsNullAndNoOrganizationGrant() {
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)).thenReturn(false);

        assertFalse(service.hasActionUnitWritePermission(userInfo, null));
    }

    @Test
    void hasActionUnitWritePermission_returnsFalse_whenNeitherOrganizationNorProjectGrantsIt() {
        ActionUnitDTO actionUnit = new ActionUnitDTO();
        actionUnit.setId(5L);

        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.PROJECT_MANAGE_SETTINGS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.PROJECT_MANAGE_SETTINGS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInActionUnit(1L, 5L, PermissionConstants.PROJECT_MANAGE_SETTINGS)).thenReturn(false);

        assertFalse(service.hasActionUnitWritePermission(userInfo, actionUnit));
    }

    // ------------------------------------------------------------------
    // actionUnitIdsWithPermission
    // ------------------------------------------------------------------

    @Test
    void actionUnitIdsWithPermission_returnsEmpty_whenNoIdsGiven() {
        Set<Long> result = service.actionUnitIdsWithPermission(userInfo, List.of(), PermissionConstants.PROJECT_EDIT_RECORDING_UNITS);

        assertThat(result).isEmpty();
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void actionUnitIdsWithPermission_returnsEveryId_whenOrganizationGrantsIt() {
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)).thenReturn(true);

        Set<Long> result = service.actionUnitIdsWithPermission(userInfo, List.of(5L, 6L, 7L), PermissionConstants.PROJECT_EDIT_RECORDING_UNITS);

        assertThat(result).containsExactlyInAnyOrder(5L, 6L, 7L);
        verify(assignmentRepository, never()).findActionUnitIdsWithPermission(anyLong(), any(), anyString());
    }

    @Test
    void actionUnitIdsWithPermission_batchesTheProjectScopedQuery_whenNoOrganizationGrant() {
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)).thenReturn(false);
        when(assignmentRepository.findActionUnitIdsWithPermission(1L, Set.of(5L, 6L), PermissionConstants.PROJECT_EDIT_RECORDING_UNITS))
                .thenReturn(Set.of(5L));

        Set<Long> result = service.actionUnitIdsWithPermission(userInfo, List.of(5L, 6L), PermissionConstants.PROJECT_EDIT_RECORDING_UNITS);

        assertThat(result).containsExactly(5L);
        // exactly one query for the whole batch, not one per action unit
        verify(assignmentRepository, times(1)).findActionUnitIdsWithPermission(anyLong(), any(), anyString());
    }

    @Test
    void actionUnitIdsWithPermission_returnsEmpty_whenPersonIsNull() {
        UserInfo anonymous = new UserInfo(institution, null, "fr");

        Set<Long> result = service.actionUnitIdsWithPermission(anonymous, List.of(5L), PermissionConstants.PROJECT_EDIT_RECORDING_UNITS);

        assertThat(result).isEmpty();
    }

    @Test
    void actionUnitIdsWithPermission_twoCodes_shortCircuitsOnEitherOrganizationGrant() {
        // hasActionUnitWritePermission's shape : an org-wide PROJECT_MANAGE_SETTINGS grant must short
        // circuit just like ORGANIZATION_MANAGE_ACTIONS would, even though it's the "project" code here
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.PROJECT_MANAGE_SETTINGS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(1L, 10L, PermissionConstants.PROJECT_MANAGE_SETTINGS)).thenReturn(true);

        Set<Long> result = service.actionUnitIdsWithPermission(userInfo, List.of(5L, 6L),
                PermissionConstants.ORGANIZATION_MANAGE_ACTIONS, PermissionConstants.PROJECT_MANAGE_SETTINGS);

        assertThat(result).containsExactlyInAnyOrder(5L, 6L);
        verify(assignmentRepository, never()).findActionUnitIdsWithPermission(anyLong(), any(), anyString());
    }

    // ------------------------------------------------------------------
    // institutionIdsWithOrganizationPermission
    // ------------------------------------------------------------------

    private static InstitutionDTO institutionWithId(Long id) {
        InstitutionDTO dto = new InstitutionDTO();
        dto.setId(id);
        return dto;
    }

    @Test
    void institutionIdsWithOrganizationPermission_returnsEmpty_whenNoInstitutionsGiven() {
        Set<Long> result = service.institutionIdsWithOrganizationPermission(person, List.of(), CODE);

        assertThat(result).isEmpty();
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void institutionIdsWithOrganizationPermission_returnsEmpty_whenPersonIsNull() {
        Set<Long> result = service.institutionIdsWithOrganizationPermission(null, List.of(institution), CODE);

        assertThat(result).isEmpty();
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void institutionIdsWithOrganizationPermission_returnsEveryId_whenInstanceProfileGrantsIt() {
        when(assignmentRepository.personHasInstancePermission(1L, CODE)).thenReturn(true);

        Set<Long> result = service.institutionIdsWithOrganizationPermission(
                person, List.of(institutionWithId(10L), institutionWithId(20L)), CODE);

        assertThat(result).containsExactlyInAnyOrder(10L, 20L);
        verify(assignmentRepository, never()).findInstitutionIdsWithPermission(anyLong(), any(), anyString());
    }

    @Test
    void institutionIdsWithOrganizationPermission_batchesTheQuery_whenNoInstanceGrant() {
        when(assignmentRepository.personHasInstancePermission(1L, CODE)).thenReturn(false);
        when(assignmentRepository.findInstitutionIdsWithPermission(1L, Set.of(10L, 20L), CODE))
                .thenReturn(Set.of(10L));

        Set<Long> result = service.institutionIdsWithOrganizationPermission(
                person, List.of(institutionWithId(10L), institutionWithId(20L)), CODE);

        assertThat(result).containsExactly(10L);
        // exactly one query for the whole batch, not one per institution
        verify(assignmentRepository, times(1)).findInstitutionIdsWithPermission(anyLong(), any(), anyString());
    }

    // ------------------------------------------------------------------
    // institutionIdsPersonCanAccess
    // ------------------------------------------------------------------

    @Test
    void institutionIdsPersonCanAccess_returnsEmpty_whenNoInstitutionsGiven() {
        Set<Long> result = service.institutionIdsPersonCanAccess(person, List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void institutionIdsPersonCanAccess_returnsEmpty_whenPersonIsNull() {
        Set<Long> result = service.institutionIdsPersonCanAccess(null, List.of(institution));

        assertThat(result).isEmpty();
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void institutionIdsPersonCanAccess_returnsEveryId_whenInstanceManageOrganizationsSettingsGrantsIt() {
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_SETTINGS))
                .thenReturn(true);

        Set<Long> result = service.institutionIdsPersonCanAccess(
                person, List.of(institutionWithId(10L), institutionWithId(20L)));

        assertThat(result).containsExactlyInAnyOrder(10L, 20L);
        verify(assignmentRepository, never()).findInstitutionIdsWithAnyProfile(anyLong(), any());
        verify(assignmentRepository, never()).findInstitutionIdsWithPermission(anyLong(), any(), anyString());
    }

    @Test
    void institutionIdsPersonCanAccess_returnsUnionOfMembershipAndManageSettings_whenNoInstanceGrant() {
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_SETTINGS))
                .thenReturn(false);
        when(assignmentRepository.findInstitutionIdsWithAnyProfile(1L, Set.of(10L, 20L)))
                .thenReturn(Set.of(10L));
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS))
                .thenReturn(false);
        when(assignmentRepository.findInstitutionIdsWithPermission(1L, Set.of(10L, 20L), PermissionConstants.ORGANIZATION_MANAGE_SETTINGS))
                .thenReturn(Set.of(20L));

        Set<Long> result = service.institutionIdsPersonCanAccess(
                person, List.of(institutionWithId(10L), institutionWithId(20L)));

        // 10L via membership, 20L via manage-settings permission — union of both sources
        assertThat(result).containsExactlyInAnyOrder(10L, 20L);
    }

    // ------------------------------------------------------------------
    // institutionIdsPersonCanManageSettings
    // ------------------------------------------------------------------

    @Test
    void institutionIdsPersonCanManageSettings_returnsEmpty_whenNoInstitutionsGiven() {
        Set<Long> result = service.institutionIdsPersonCanManageSettings(person, List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void institutionIdsPersonCanManageSettings_returnsEmpty_whenPersonIsNull() {
        Set<Long> result = service.institutionIdsPersonCanManageSettings(null, List.of(institution));

        assertThat(result).isEmpty();
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void institutionIdsPersonCanManageSettings_returnsEveryId_whenInstanceManageSettingsGrantsIt() {
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_MANAGE_SETTINGS))
                .thenReturn(true);

        Set<Long> result = service.institutionIdsPersonCanManageSettings(
                person, List.of(institutionWithId(10L), institutionWithId(20L)));

        assertThat(result).containsExactlyInAnyOrder(10L, 20L);
        verify(assignmentRepository, never()).findInstitutionIdsWithPermission(anyLong(), any(), anyString());
    }

    @Test
    void institutionIdsPersonCanManageSettings_delegatesToOrganizationPermission_whenNoInstanceGrant() {
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.INSTANCE_MANAGE_SETTINGS))
                .thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(1L, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS))
                .thenReturn(false);
        when(assignmentRepository.findInstitutionIdsWithPermission(1L, Set.of(10L), PermissionConstants.ORGANIZATION_MANAGE_SETTINGS))
                .thenReturn(Set.of(10L));

        Set<Long> result = service.institutionIdsPersonCanManageSettings(person, List.of(institutionWithId(10L)));

        assertThat(result).containsExactly(10L);
    }
}
