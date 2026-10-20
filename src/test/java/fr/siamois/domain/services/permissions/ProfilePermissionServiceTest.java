package fr.siamois.domain.services.permissions;

import fr.siamois.domain.models.UserInfo;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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

    @Test
    void hasDocumentWritePermission_checksTheDocumentsOwnProjectCodes() {
        when(assignmentRepository.personHasInstancePermission(eq(3L), anyString())).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(eq(3L), eq(12L), anyString())).thenReturn(false);
        when(assignmentRepository.personHasPermissionInActionUnit(3L, 9L, PermissionConstants.PROJECT_EDIT_DOCUMENTS)).thenReturn(true);
        fr.siamois.dto.entity.DocumentDTO document = new fr.siamois.dto.entity.DocumentDTO();
        fr.siamois.dto.entity.ActionUnitSummaryDTO project = new fr.siamois.dto.entity.ActionUnitSummaryDTO();
        project.setId(9L);
        document.setActionUnit(project);
        UserInfo info = new UserInfo(institution, person, null);

        assertTrue(profilePermissionService.hasDocumentWritePermission(info, document));
        // a document without a project can only be written through an organisation or instance right
        assertFalse(profilePermissionService.hasDocumentWritePermission(info, new fr.siamois.dto.entity.DocumentDTO()));
    }

    // ---- actionUnitIdsGranting — which projects of the institution a capability covers ----

    @Test
    void actionUnitIdsGranting_organizationWideCounterpart_coversEveryProject() {
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_EDIT_PHASES)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_EDIT_PHASES)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_EDIT_PHASES)).thenReturn(true);

        assertNull(profilePermissionService.actionUnitIdsGranting(new UserInfo(institution, person, null),
                PermissionConstants.INSTANCE_EDIT_PHASES, PermissionConstants.ORGANIZATION_EDIT_PHASES, PermissionConstants.PROJECT_EDIT_PHASES));
    }

    @Test
    void actionUnitIdsGranting_projectProfilesOnly_listsTheirProjects() {
        when(assignmentRepository.personHasInstancePermission(eq(3L), anyString())).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_EDIT_PHASES)).thenReturn(false);
        when(assignmentRepository.findInstitutionActionUnitIdsWithPermission(3L, 12L, PermissionConstants.PROJECT_EDIT_PHASES))
                .thenReturn(java.util.Set.of(4L, 5L));

        assertEquals(java.util.Set.of(4L, 5L), profilePermissionService.actionUnitIdsGranting(new UserInfo(institution, person, null),
                PermissionConstants.INSTANCE_EDIT_PHASES, PermissionConstants.ORGANIZATION_EDIT_PHASES, PermissionConstants.PROJECT_EDIT_PHASES));
    }

    @Test
    void grantsOf_listsProjectsBeforeOrganisations_withTargetAndPermissions() {
        fr.siamois.domain.models.permissions.Permission edit = new fr.siamois.domain.models.permissions.Permission();
        edit.setCode(PermissionConstants.PROJECT_EDIT_FINDS);
        fr.siamois.domain.models.permissions.Permission manage = new fr.siamois.domain.models.permissions.Permission();
        manage.setCode(PermissionConstants.PROJECT_MANAGE_SETTINGS);

        fr.siamois.domain.models.institution.Institution org = new fr.siamois.domain.models.institution.Institution();
        org.setId(12L);
        org.setName("Bibracte");
        fr.siamois.domain.models.actionunit.ActionUnit project = new fr.siamois.domain.models.actionunit.ActionUnit();
        project.setId(7L);
        project.setName("Fouille du Mont Beuvray");

        fr.siamois.domain.models.permissions.Profile orgProfile = new fr.siamois.domain.models.permissions.Profile();
        orgProfile.setCode("ORGANIZATION_MEMBER");
        orgProfile.setName("Membre");
        orgProfile.setScope(fr.siamois.domain.models.permissions.PermissionScopeType.ORGANISATION);
        orgProfile.setInstitution(org);
        fr.siamois.domain.models.permissions.Profile projectProfile = new fr.siamois.domain.models.permissions.Profile();
        projectProfile.setCode("PROJECT_MANAGER");
        projectProfile.setName("Gestionnaire");
        projectProfile.setScope(fr.siamois.domain.models.permissions.PermissionScopeType.PROJECT);
        projectProfile.setInstitution(org);
        projectProfile.setActionUnit(project);
        projectProfile.getPermissions().add(manage);
        projectProfile.getPermissions().add(edit);

        when(assignmentRepository.findProfilesOfPerson(3L)).thenReturn(java.util.List.of(orgProfile, projectProfile));

        var grants = profilePermissionService.grantsOf(person);

        assertThat(grants).hasSize(2);
        assertThat(grants.get(0).isProject()).isTrue();
        assertThat(grants.get(0).getTargetName()).isEqualTo("Fouille du Mont Beuvray");
        assertThat(grants.get(0).getProfileName()).isEqualTo("Gestionnaire");
        assertThat(grants.get(0).getPermissionCodes())
                .containsExactly(PermissionConstants.PROJECT_EDIT_FINDS, PermissionConstants.PROJECT_MANAGE_SETTINGS);
        assertThat(grants.get(1).isOrganisation()).isTrue();
        assertThat(grants.get(1).getTargetName()).isEqualTo("Bibracte");
    }

    @Test
    void grantsOf_instanceProfileWithoutNameNorTarget_fallsBackToItsCode() {
        fr.siamois.domain.models.permissions.Profile instanceProfile = new fr.siamois.domain.models.permissions.Profile();
        instanceProfile.setCode("SUPER_ADMIN");
        instanceProfile.setScope(fr.siamois.domain.models.permissions.PermissionScopeType.INSTANCE);
        when(assignmentRepository.findProfilesOfPerson(3L)).thenReturn(java.util.List.of(instanceProfile));

        var grant = profilePermissionService.grantsOf(person).get(0);

        assertThat(grant.getProfileName()).isEqualTo("SUPER_ADMIN");
        assertThat(grant.isInstance()).isTrue();
        assertThat(grant.isProject()).isFalse();
        assertThat(grant.isOrganisation()).isFalse();
        assertThat(grant.getTargetName()).isNull();
        assertThat(grant.getInstitutionId()).isNull();
        assertThat(grant.getActionUnitId()).isNull();
    }

    @Test
    void hasProjectSettingsPermission_shouldBeGrantedToOrganizationProjectManager() {
        UserInfo user = new UserInfo(institution, person, null);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS))
                .thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))
                .thenReturn(true);

        assertTrue(profilePermissionService.hasProjectSettingsPermission(user, 7L));
    }

    @Test
    void hasProjectSettingsPermission_shouldFallBackToProjectProfile() {
        UserInfo user = new UserInfo(institution, person, null);
        when(assignmentRepository.personHasPermissionInActionUnit(3L, 7L, PermissionConstants.PROJECT_MANAGE_SETTINGS))
                .thenReturn(true);

        assertTrue(profilePermissionService.hasProjectSettingsPermission(user, 7L));
        assertFalse(profilePermissionService.hasProjectSettingsPermission(user, 8L));
    }

    @Test
    void hasProjectSettingsPermission_shouldBeDeniedWithoutUser() {
        assertFalse(profilePermissionService.hasProjectSettingsPermission(null, 7L));
    }

    @Test
    void actionUnitIdsWithProjectSettingsPermission_shouldBeEmptyWithoutUser() {
        assertThat(profilePermissionService.actionUnitIdsWithProjectSettingsPermission(null, Set.of(7L))).isEmpty();
    }

    @Test
    void actionUnitIdsWithProjectSettingsPermission_shouldReturnEveryProjectForInstanceManager() {
        UserInfo user = new UserInfo(institution, person, null);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS))
                .thenReturn(true);

        assertThat(profilePermissionService.actionUnitIdsWithProjectSettingsPermission(user, java.util.Arrays.asList(7L, null, 8L)))
                .containsExactlyInAnyOrder(7L, 8L);
        assertThat(profilePermissionService.actionUnitIdsWithProjectSettingsPermission(user, null)).isEmpty();
    }

    @Test
    void actionUnitIdsWithProjectSettingsPermission_shouldReturnEveryProjectForOrganizationProjectManager() {
        UserInfo user = new UserInfo(institution, person, null);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS))
                .thenReturn(true);

        assertThat(profilePermissionService.actionUnitIdsWithProjectSettingsPermission(user, Set.of(7L, 8L)))
                .containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    void actionUnitIdsWithProjectSettingsPermission_shouldFallBackToProjectProfiles() {
        UserInfo user = new UserInfo(institution, person, null);
        when(assignmentRepository.findActionUnitIdsWithPermission(
                eq(3L), org.mockito.ArgumentMatchers.<java.util.Collection<Long>>any(), eq(PermissionConstants.PROJECT_MANAGE_SETTINGS)))
                .thenReturn(Set.of(7L));

        assertThat(profilePermissionService.actionUnitIdsWithProjectSettingsPermission(user, Set.of(7L, 8L)))
                .containsExactly(7L);
    }

    // ---- deletion rights: the records of a project, and the project itself -------------------

    private UserInfo userInfo() {
        return new UserInfo(institution, person, "fr");
    }

    private void denyEverythingByDefault() {
        lenient().when(assignmentRepository.personHasInstancePermission(eq(3L), anyString())).thenReturn(false);
        lenient().when(assignmentRepository.personHasPermissionInInstitution(eq(3L), eq(12L), anyString())).thenReturn(false);
        lenient().when(assignmentRepository.personHasPermissionInActionUnit(eq(3L), eq(99L), anyString())).thenReturn(false);
    }

    @Test
    void hasDeleteEntitiesPermission_nullUser_isFalse() {
        assertFalse(profilePermissionService.hasDeleteEntitiesPermission(null, 99L));
    }

    @Test
    void hasDeleteEntitiesPermission_noGrant_isFalse() {
        denyEverythingByDefault();
        assertFalse(profilePermissionService.hasDeleteEntitiesPermission(userInfo(), 99L));
    }

    @Test
    void hasDeleteEntitiesPermission_instanceGrant_isTrue() {
        denyEverythingByDefault();
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_DELETE_ENTITIES)).thenReturn(true);
        assertTrue(profilePermissionService.hasDeleteEntitiesPermission(userInfo(), 99L));
    }

    @Test
    void hasDeleteEntitiesPermission_organizationGrant_isTrue() {
        denyEverythingByDefault();
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_DELETE_ENTITIES))
                .thenReturn(true);
        assertTrue(profilePermissionService.hasDeleteEntitiesPermission(userInfo(), 99L));
    }

    @Test
    void hasDeleteEntitiesPermission_projectGrant_isTrue_andEditRightAloneIsNot() {
        denyEverythingByDefault();
        lenient().when(assignmentRepository.personHasPermissionInActionUnit(3L, 99L, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS))
                .thenReturn(true);
        assertFalse(profilePermissionService.hasDeleteEntitiesPermission(userInfo(), 99L));

        when(assignmentRepository.personHasPermissionInActionUnit(3L, 99L, PermissionConstants.PROJECT_DELETE_ENTITIES))
                .thenReturn(true);
        assertTrue(profilePermissionService.hasDeleteEntitiesPermission(userInfo(), 99L));
    }

    @Test
    void hasDeleteProjectPermission_userInfo_nullUser_isFalse() {
        assertFalse(profilePermissionService.hasDeleteProjectPermission((UserInfo) null, 99L));
    }

    @Test
    void hasDeleteProjectPermission_userInfo_projectGrant_isTrue() {
        denyEverythingByDefault();
        assertFalse(profilePermissionService.hasDeleteProjectPermission(userInfo(), 99L));
        when(assignmentRepository.personHasPermissionInActionUnit(3L, 99L, PermissionConstants.PROJECT_DELETE_PROJECT))
                .thenReturn(true);
        assertTrue(profilePermissionService.hasDeleteProjectPermission(userInfo(), 99L));
    }

    @Test
    void hasDeleteProjectPermission_person_nullPerson_isFalse() {
        assertFalse(profilePermissionService.hasDeleteProjectPermission(null, institution, 99L));
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void hasDeleteProjectPermission_person_instanceOrganizationOrProject() {
        denyEverythingByDefault();
        assertFalse(profilePermissionService.hasDeleteProjectPermission(person, institution, 99L));
        assertFalse(profilePermissionService.hasDeleteProjectPermission(person, institution, null));

        when(assignmentRepository.personHasPermissionInActionUnit(3L, 99L, PermissionConstants.PROJECT_DELETE_PROJECT))
                .thenReturn(true);
        assertTrue(profilePermissionService.hasDeleteProjectPermission(person, institution, 99L));
    }

    @Test
    void hasDeleteProjectPermission_person_instanceGrant_isTrue() {
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_DELETE_PROJECT)).thenReturn(true);
        assertTrue(profilePermissionService.hasDeleteProjectPermission(person, institution, 99L));
    }

    @Test
    void actionUnitIdsWithDeleteProjectPermission_emptyOrNull_returnsEmptyWithoutQuerying() {
        assertThat(profilePermissionService.actionUnitIdsWithDeleteProjectPermission(person, Map.of())).isEmpty();
        assertThat(profilePermissionService.actionUnitIdsWithDeleteProjectPermission(null, Map.of(1L, institution))).isEmpty();
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void actionUnitIdsWithDeleteProjectPermission_instanceGrant_grantsAll() {
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_DELETE_PROJECT)).thenReturn(true);
        Map<Long, InstitutionDTO> byActionUnit = new LinkedHashMap<>();
        byActionUnit.put(1L, institution);
        byActionUnit.put(2L, institution);

        assertThat(profilePermissionService.actionUnitIdsWithDeleteProjectPermission(person, byActionUnit))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void actionUnitIdsWithDeleteProjectPermission_mixed_appliesOrganizationGrantThenProjectGrant() {
        InstitutionDTO other = new InstitutionDTO();
        other.setId(77L);
        Map<Long, InstitutionDTO> byActionUnit = new LinkedHashMap<>();
        byActionUnit.put(1L, institution); // organisation grant
        byActionUnit.put(2L, other);       // project grant
        byActionUnit.put(3L, other);       // nothing

        when(assignmentRepository.personHasInstancePermission(eq(3L), anyString())).thenReturn(false);
        when(assignmentRepository.findInstitutionIdsWithPermission(eq(3L), anySet(), eq(PermissionConstants.ORGANIZATION_DELETE_PROJECT)))
                .thenReturn(Set.of(12L));
        when(assignmentRepository.findActionUnitIdsWithPermission(eq(3L), anySet(), eq(PermissionConstants.PROJECT_DELETE_PROJECT)))
                .thenReturn(Set.of(2L));

        assertThat(profilePermissionService.actionUnitIdsWithDeleteProjectPermission(person, byActionUnit))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void hasDefaultViewEditPermission_withoutProject_needsTheInstanceOrOrganizationRight() {
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.INSTANCE_EDIT_DEFAULT_VIEWS)).thenReturn(false);
        when(assignmentRepository.personHasInstancePermission(3L, PermissionConstants.ORGANIZATION_EDIT_DEFAULT_VIEWS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_EDIT_DEFAULT_VIEWS)).thenReturn(true);

        assertTrue(profilePermissionService.hasDefaultViewEditPermission(userInfo(), null));
    }

    @Test
    void hasDefaultViewEditPermission_withoutProjectAndWithoutAnyRight_isFalse() {
        when(assignmentRepository.personHasInstancePermission(eq(3L), anyString())).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_EDIT_DEFAULT_VIEWS)).thenReturn(false);

        assertFalse(profilePermissionService.hasDefaultViewEditPermission(userInfo(), null));
    }

    @Test
    void hasDefaultViewEditPermission_onAProject_acceptsAProjectGrant() {
        when(assignmentRepository.personHasInstancePermission(eq(3L), anyString())).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_EDIT_DEFAULT_VIEWS)).thenReturn(false);
        when(assignmentRepository.personHasPermissionInActionUnit(3L, 40L, PermissionConstants.PROJECT_EDIT_DEFAULT_VIEWS)).thenReturn(true);

        assertTrue(profilePermissionService.hasDefaultViewEditPermission(userInfo(), 40L));
    }

    @Test
    void hasDefaultViewEditPermission_onAProject_acceptsTheOrganizationGrantWithoutLookingAtTheProject() {
        when(assignmentRepository.personHasInstancePermission(eq(3L), anyString())).thenReturn(false);
        when(assignmentRepository.personHasPermissionInInstitution(3L, 12L, PermissionConstants.ORGANIZATION_EDIT_DEFAULT_VIEWS)).thenReturn(true);

        assertTrue(profilePermissionService.hasDefaultViewEditPermission(userInfo(), 40L));
    }
}
