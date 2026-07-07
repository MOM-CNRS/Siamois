package fr.siamois.domain.services;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PermissionScopeType;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.dto.entity.ProjectMemberDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectMembersServiceInterfaceImplTest {

    @Mock
    private PersonProfileAssignmentRepository personProfileAssignmentRepository;
    @Mock
    private ProfileService profileService;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private ProfileMapper profileMapper;
    @Mock
    private PersonProfileAssignmentService personProfileAssignmentService;

    @InjectMocks
    private ProjectMembersServiceInterfaceImpl projectMembersService;

    private ActionUnitDTO project;
    private Person member;
    private PersonDTO memberDTO;
    private Profile memberProfile;
    private ProfileDTO memberProfileDTO;

    @BeforeEach
    void setUp() {
        project = new ActionUnitDTO();
        project.setId(1L);

        member = new Person();
        member.setId(10L);
        member.setUsername("member");

        memberDTO = new PersonDTO();
        memberDTO.setId(10L);
        memberDTO.setUsername("member");

        memberProfile = new Profile();
        memberProfile.setId(100L);
        memberProfile.setCode(ProfileConstants.PROJECT_MEMBER);
        memberProfile.setScope(PermissionScopeType.PROJECT);

        memberProfileDTO = new ProfileDTO();
        memberProfileDTO.setId(100L);
        memberProfileDTO.setCode(ProfileConstants.PROJECT_MEMBER);
        memberProfileDTO.setScope(PermissionScopeType.PROJECT);
    }

    @Test
    void findMembersOf_shouldReturnMembersWithTheirAssignedProfiles() {
        Profile managerProfile = new Profile();
        managerProfile.setId(101L);
        managerProfile.setCode(ProfileConstants.PROJECT_MANAGER);
        managerProfile.setScope(PermissionScopeType.PROJECT);

        ProfileDTO managerProfileDTO = new ProfileDTO();
        managerProfileDTO.setId(101L);
        managerProfileDTO.setCode(ProfileConstants.PROJECT_MANAGER);
        managerProfileDTO.setScope(PermissionScopeType.PROJECT);

        when(personProfileAssignmentRepository.findAllAssignmentsByActionUnitId(project.getId()))
                .thenReturn(List.of(assignment(member, memberProfile), assignment(member, managerProfile)));
        when(personMapper.convert(member)).thenReturn(memberDTO);
        when(profileMapper.convert(memberProfile)).thenReturn(memberProfileDTO);
        when(profileMapper.convert(managerProfile)).thenReturn(managerProfileDTO);

        List<ProjectMemberDTO> result = projectMembersService.findMembersOf(project);

        assertThat(result).hasSize(1);
        ProjectMemberDTO memberResult = result.get(0);
        assertThat(memberResult.getPerson()).isEqualTo(memberDTO);
        assertThat(memberResult.getProfiles()).containsExactly(memberProfileDTO, managerProfileDTO);
    }

    @Test
    void findMembersOf_shouldReturnOneEntryPerMember() {
        Person otherMember = new Person();
        otherMember.setId(11L);
        otherMember.setUsername("other");

        PersonDTO otherMemberDTO = new PersonDTO();
        otherMemberDTO.setId(11L);
        otherMemberDTO.setUsername("other");

        when(personProfileAssignmentRepository.findAllAssignmentsByActionUnitId(project.getId()))
                .thenReturn(List.of(assignment(member, memberProfile), assignment(otherMember, memberProfile)));
        when(personMapper.convert(member)).thenReturn(memberDTO);
        when(personMapper.convert(otherMember)).thenReturn(otherMemberDTO);
        when(profileMapper.convert(memberProfile)).thenReturn(memberProfileDTO);

        List<ProjectMemberDTO> result = projectMembersService.findMembersOf(project);

        assertThat(result)
                .hasSize(2)
                .extracting(ProjectMemberDTO::getPerson)
                .containsExactlyInAnyOrder(memberDTO, otherMemberDTO);
    }

    @Test
    void findMembersOf_shouldReturnEmptyList_whenProjectHasNoMember() {
        when(personProfileAssignmentRepository.findAllAssignmentsByActionUnitId(project.getId()))
                .thenReturn(List.of());

        List<ProjectMemberDTO> result = projectMembersService.findMembersOf(project);

        assertThat(result).isEmpty();
        verify(profileMapper, never()).convert(any(Profile.class));
        verify(personMapper, never()).convert(any(Person.class));
    }

    @Test
    void findAvailableProfiles_shouldReturnProfilesOfProject() {
        when(profileService.findAllProfilesByActionUnit(project)).thenReturn(List.of(memberProfileDTO));

        List<ProfileDTO> result = projectMembersService.findAvailableProfiles(project);

        assertThat(result).containsExactly(memberProfileDTO);
        verify(profileService, times(1)).findAllProfilesByActionUnit(project);
    }

    @Test
    void findAvailableProfiles_shouldReturnEmptyList_whenProjectHasNoProfile() {
        when(profileService.findAllProfilesByActionUnit(project)).thenReturn(List.of());

        List<ProfileDTO> result = projectMembersService.findAvailableProfiles(project);

        assertThat(result).isEmpty();
    }

    @Test
    void addMemberToProject_shouldDelegateToAssignmentService() {
        List<ProfileDTO> profiles = List.of(memberProfileDTO);

        ProjectMemberDTO expected = new ProjectMemberDTO();
        expected.setPerson(memberDTO);
        expected.setProfiles(profiles);

        when(personProfileAssignmentService.addToProjectMembers(project, memberDTO, profiles))
                .thenReturn(expected);

        ProjectMemberDTO result = projectMembersService.addMemberToProject(project, memberDTO, profiles);

        assertThat(result).isEqualTo(expected);
        verify(personProfileAssignmentService, times(1)).addToProjectMembers(project, memberDTO, profiles);
    }

    private PersonProfileAssignment assignment(Person person, Profile profile) {
        PersonProfileAssignment assignment = new PersonProfileAssignment();
        assignment.setPerson(person);
        assignment.setProfile(profile);
        return assignment;
    }
}
