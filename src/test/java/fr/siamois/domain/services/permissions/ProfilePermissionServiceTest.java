package fr.siamois.domain.services.permissions;

import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfilePermissionServiceTest {

    @Mock
    private PersonProfileAssignmentRepository assignmentRepository;

    @InjectMocks
    private ProfilePermissionService profilePermissionService;

    private PersonDTO person;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        person = new PersonDTO();
        person.setId(3L);
        institution = new InstitutionDTO();
        institution.setId(12L);
    }

    @Test
    void canAccessInstitution_shouldReturnTrueWhenPersonHasAnyProfileInInstitution() {
        when(assignmentRepository.personHasAnyProfileInInstitution(3L, 12L)).thenReturn(true);

        assertTrue(profilePermissionService.canAccessInstitution(person, institution));
    }

    @Test
    void canAccessInstitution_shouldReturnTrueWhenPersonManagesTheInstitution() {
        when(assignmentRepository.personHasAnyProfileInInstitution(3L, 12L)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_SETTINGS))
                .thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS))
                .thenReturn(true);

        assertTrue(profilePermissionService.canAccessInstitution(person, institution));
    }

    @Test
    void canAccessInstitution_shouldReturnFalseWhenPersonHasNoProfileInInstitution() {
        when(assignmentRepository.personHasAnyProfileInInstitution(3L, 12L)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_SETTINGS))
                .thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS))
                .thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS))
                .thenReturn(false);

        assertFalse(profilePermissionService.canAccessInstitution(person, institution));
    }

    @Test
    void canAccessInstitution_shouldReturnFalseWhenInstitutionIsNull() {
        assertFalse(profilePermissionService.canAccessInstitution(person, null));
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void canAccessInstitution_shouldReturnFalseWhenPersonIsNull() {
        assertFalse(profilePermissionService.canAccessInstitution(null, institution));
        verifyNoInteractions(assignmentRepository);
    }

    // ---- hasActionUnitWritePermission(PersonDTO, InstitutionDTO, Long) ----------------------
    // The PersonDTO/InstitutionDTO overload used by the REST API (plan §3/§5), which can span
    // several institutions in one request — unlike hasActionUnitWritePermission(UserInfo, ...),
    // always scoped to sessionSettingsBean's one current institution.

    @Test
    void hasActionUnitWritePermission_instanceScoped_grantsRegardlessOfInstitutionOrActionUnit() {
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS))
                .thenReturn(true);

        assertTrue(profilePermissionService.hasActionUnitWritePermission(person, institution, 99L));
    }

    @Test
    void hasActionUnitWritePermission_organizationScoped_grantsOnAnyActionUnitInThatInstitution() {
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))
                .thenReturn(true);

        assertTrue(profilePermissionService.hasActionUnitWritePermission(person, institution, 99L));
    }

    @Test
    void hasActionUnitWritePermission_projectScoped_fallsThroughToActionUnitAssignment() {
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.personHasPermissionInActionUnit(3L, 99L, PermissionConstants.PROJECT_MANAGE_SETTINGS))
                .thenReturn(true);

        assertTrue(profilePermissionService.hasActionUnitWritePermission(person, institution, 99L));
    }

    @Test
    void hasActionUnitWritePermission_noGrant_returnsFalse() {
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.personHasPermissionInActionUnit(3L, 99L, PermissionConstants.PROJECT_MANAGE_SETTINGS))
                .thenReturn(false);

        assertFalse(profilePermissionService.hasActionUnitWritePermission(person, institution, 99L));
    }

    @Test
    void hasActionUnitWritePermission_nullPerson_returnsFalseWithoutQuerying() {
        assertFalse(profilePermissionService.hasActionUnitWritePermission(null, institution, 99L));
        verifyNoInteractions(assignmentRepository);
    }

    // ---- actionUnitIdsWithWritePermission(PersonDTO, Map<Long, InstitutionDTO>) -------------
    // Bulk, institution-aware version for a list page that may span several institutions.

    @Test
    void actionUnitIdsWithWritePermission_instanceScoped_grantsAllRegardlessOfInstitution() {
        InstitutionDTO otherInstitution = new InstitutionDTO();
        otherInstitution.setId(77L);
        Map<Long, InstitutionDTO> byActionUnit = new LinkedHashMap<>();
        byActionUnit.put(1L, institution);
        byActionUnit.put(2L, otherInstitution);

        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS))
                .thenReturn(true);

        assertThat(profilePermissionService.actionUnitIdsWithWritePermission(person, byActionUnit))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void actionUnitIdsWithWritePermission_mixedInstitutions_appliesOrgGrantOnlyToItsOwnInstitution() {
        InstitutionDTO grantedInstitution = new InstitutionDTO();
        grantedInstitution.setId(12L);
        InstitutionDTO ungrantedInstitution = new InstitutionDTO();
        ungrantedInstitution.setId(77L);

        Map<Long, InstitutionDTO> byActionUnit = new LinkedHashMap<>();
        byActionUnit.put(1L, grantedInstitution);   // org-granted institution -> included via org check
        byActionUnit.put(2L, ungrantedInstitution);  // not org-granted -> falls through to project-level check
        byActionUnit.put(3L, ungrantedInstitution);  // same institution, no project-level grant -> excluded

        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS))
                .thenReturn(false);
        // institutionIdsWithOrganizationPermission's own instance short-circuit for ORGANIZATION_MANAGE_ACTIONS
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.findInstitutionIdsWithPermission(eq(3L), anySet(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)))
                .thenReturn(Set.of(12L));
        when(assignmentRepository.findActionUnitIdsWithPermission(eq(3L), anySet(), eq(PermissionConstants.PROJECT_MANAGE_SETTINGS)))
                .thenReturn(Set.of(2L));

        assertThat(profilePermissionService.actionUnitIdsWithWritePermission(person, byActionUnit))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void actionUnitIdsWithWritePermission_emptyMap_returnsEmptySetWithoutQuerying() {
        assertThat(profilePermissionService.actionUnitIdsWithWritePermission(person, Map.of())).isEmpty();
        verifyNoInteractions(assignmentRepository);
    }

    // ---- hasActionUnitCreatePermission(PersonDTO, InstitutionDTO) / institutionIdsWith... ----

    @Test
    void hasActionUnitCreatePermission_personInstitution_checksBothManageAndCreateCodes() {
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_CREATE_ACTIONS))
                .thenReturn(true);

        assertTrue(profilePermissionService.hasActionUnitCreatePermission(person, institution));
    }

    @Test
    void institutionIdsWithActionUnitCreatePermission_unionsBothPermissionCodes() {
        InstitutionDTO manageOnly = new InstitutionDTO();
        manageOnly.setId(1L);
        InstitutionDTO createOnly = new InstitutionDTO();
        createOnly.setId(2L);

        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.findInstitutionIdsWithPermission(eq(3L), anySet(), eq(PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)))
                .thenReturn(Set.of(1L));
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_CREATE_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.findInstitutionIdsWithPermission(eq(3L), anySet(), eq(PermissionConstants.ORGANIZATION_CREATE_ACTIONS)))
                .thenReturn(Set.of(2L));

        assertThat(profilePermissionService.institutionIdsWithActionUnitCreatePermission(person, Set.of(manageOnly, createOnly)))
                .containsExactlyInAnyOrder(1L, 2L);
    }

}
