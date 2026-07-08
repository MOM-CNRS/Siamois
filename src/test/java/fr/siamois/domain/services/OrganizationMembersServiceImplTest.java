package fr.siamois.domain.services;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PermissionScopeType;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.InstitutionMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.infrastructure.database.repositories.permissions.ProfileRepository;
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
class OrganizationMembersServiceImplTest {

    @Mock
    private PersonProfileAssignmentRepository assignmentRepository;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private ProfileMapper profileMapper;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private PersonProfileAssignmentService personProfileAssignmentService;
    @Mock
    private ProfileService profileService;

    @InjectMocks
    private OrganizationMembersServiceImpl organizationMembersService;

    private InstitutionDTO institution;
    private Person member;
    private PersonDTO memberDTO;
    private Profile memberProfile;
    private ProfileDTO memberProfileDTO;

    @BeforeEach
    void setUp() {
        institution = new InstitutionDTO();
        institution.setId(1L);
        institution.setName("Siamois");
        institution.setIdentifier("siamois");

        member = new Person();
        member.setId(10L);
        member.setUsername("member");

        memberDTO = new PersonDTO();
        memberDTO.setId(10L);
        memberDTO.setUsername("member");

        memberProfile = new Profile();
        memberProfile.setId(100L);
        memberProfile.setCode(ProfileConstants.ORGANIZATION_MEMBER);
        memberProfile.setScope(PermissionScopeType.ORGANISATION);

        memberProfileDTO = new ProfileDTO();
        memberProfileDTO.setId(100L);
        memberProfileDTO.setCode(ProfileConstants.ORGANIZATION_MEMBER);
        memberProfileDTO.setScope(PermissionScopeType.ORGANISATION);
    }

    @Test
    void findMembersOf_shouldReturnMembersWithTheirProfiles() {
        Profile managerProfile = new Profile();
        managerProfile.setId(101L);
        managerProfile.setCode(ProfileConstants.ORGANIZATION_MANAGER);
        managerProfile.setScope(PermissionScopeType.ORGANISATION);

        ProfileDTO managerProfileDTO = new ProfileDTO();
        managerProfileDTO.setId(101L);
        managerProfileDTO.setCode(ProfileConstants.ORGANIZATION_MANAGER);
        managerProfileDTO.setScope(PermissionScopeType.ORGANISATION);

        when(assignmentRepository.findAllAssignmentsByInstitutionId(institution.getId()))
                .thenReturn(List.of(assignment(member, memberProfile), assignment(member, managerProfile)));
        when(personMapper.convert(member)).thenReturn(memberDTO);
        when(profileMapper.convert(memberProfile)).thenReturn(memberProfileDTO);
        when(profileMapper.convert(managerProfile)).thenReturn(managerProfileDTO);

        List<InstitutionMemberDTO> result = organizationMembersService.findMembersOf(institution);

        assertThat(result).hasSize(1);
        InstitutionMemberDTO memberResult = result.get(0);
        assertThat(memberResult.getPerson()).isEqualTo(memberDTO);
        assertThat(memberResult.getProfiles()).hasSize(2);
    }

    @Test
    void findMembersOf_shouldReturnOneEntryPerMember() {
        Person otherMember = new Person();
        otherMember.setId(11L);
        otherMember.setUsername("other");

        PersonDTO otherMemberDTO = new PersonDTO();
        otherMemberDTO.setId(11L);
        otherMemberDTO.setUsername("other");

        when(assignmentRepository.findAllAssignmentsByInstitutionId(institution.getId()))
                .thenReturn(List.of(assignment(member, memberProfile), assignment(otherMember, memberProfile)));
        when(personMapper.convert(member)).thenReturn(memberDTO);
        when(personMapper.convert(otherMember)).thenReturn(otherMemberDTO);
        when(profileMapper.convert(memberProfile)).thenReturn(memberProfileDTO);

        List<InstitutionMemberDTO> result = organizationMembersService.findMembersOf(institution);

        assertThat(result)
                .hasSize(2)
                .extracting(InstitutionMemberDTO::getPerson)
                .containsExactlyInAnyOrder(memberDTO, otherMemberDTO);
    }

    @Test
    void findMembersOf_shouldReturnEmptyList_whenInstitutionHasNoMember() {
        when(assignmentRepository.findAllAssignmentsByInstitutionId(institution.getId()))
                .thenReturn(List.of());

        List<InstitutionMemberDTO> result = organizationMembersService.findMembersOf(institution);

        assertThat(result).isEmpty();
        verify(profileMapper, never()).convert(any(Profile.class));
        verify(personMapper, never()).convert(any(Person.class));
    }

    @Test
    void findAvailableProfiles_shouldReturnProfilesOfInstitution() {
        when(profileRepository.findProfilesByInstitutionId(institution.getId()))
                .thenReturn(List.of(memberProfile));
        when(profileMapper.convert(memberProfile)).thenReturn(memberProfileDTO);

        List<ProfileDTO> result = organizationMembersService.findAvailableProfiles(institution);

        assertThat(result).containsExactly(memberProfileDTO);
    }

    @Test
    void findAvailableProfiles_shouldReturnEmptyList_whenInstitutionHasNoProfile() {
        when(profileRepository.findProfilesByInstitutionId(institution.getId()))
                .thenReturn(List.of());

        List<ProfileDTO> result = organizationMembersService.findAvailableProfiles(institution);

        assertThat(result).isEmpty();
        verify(profileMapper, never()).convert(any(Profile.class));
    }

    @Test
    void addMemberToInstitution_shouldDelegateToAssignmentService() {
        List<ProfileDTO> profiles = List.of(memberProfileDTO);

        InstitutionMemberDTO expected = new InstitutionMemberDTO();
        expected.setPerson(memberDTO);
        expected.setProfiles(profiles);

        when(personProfileAssignmentService.addToInstitution(institution, memberDTO, profiles))
                .thenReturn(expected);

        InstitutionMemberDTO result = organizationMembersService.addMemberToInstitution(institution, memberDTO, profiles);

        assertThat(result).isEqualTo(expected);
        verify(personProfileAssignmentService, times(1)).addToInstitution(institution, memberDTO, profiles);
    }

    @Test
    void removeProfileFromMember_shouldRemove_whenProfileIsNotOrganizationManager() {
        InstitutionMemberDTO member = new InstitutionMemberDTO();
        member.setPerson(memberDTO);

        when(assignmentRepository.personHasAnyProfileInInstitution(memberDTO.getId(), institution.getId()))
                .thenReturn(true);

        boolean result = organizationMembersService.removeProfileFromMember(institution, member, memberProfileDTO);

        assertThat(result).isTrue();
        verify(personProfileAssignmentService, times(1)).remove(memberDTO, memberProfileDTO);
        verify(personProfileAssignmentService, never()).isNotLastOrganizationManager(any(), any());
        verify(profileService, never()).createOrGetOrganizationMemberProfile(any());
    }

    @Test
    void removeProfileFromMember_shouldRemove_whenManagerIsNotLast() {
        ProfileDTO managerProfileDTO = new ProfileDTO();
        managerProfileDTO.setId(101L);
        managerProfileDTO.setCode(ProfileConstants.ORGANIZATION_MANAGER);
        managerProfileDTO.setScope(PermissionScopeType.ORGANISATION);

        InstitutionMemberDTO member = new InstitutionMemberDTO();
        member.setPerson(memberDTO);

        when(personProfileAssignmentService.isNotLastOrganizationManager(institution, memberDTO)).thenReturn(true);
        when(assignmentRepository.personHasAnyProfileInInstitution(memberDTO.getId(), institution.getId()))
                .thenReturn(true);

        boolean result = organizationMembersService.removeProfileFromMember(institution, member, managerProfileDTO);

        assertThat(result).isTrue();
        verify(personProfileAssignmentService, times(1)).remove(memberDTO, managerProfileDTO);
    }

    @Test
    void removeProfileFromMember_shouldReassignMemberProfile_whenMemberHasNoProfileLeft() {
        InstitutionMemberDTO member = new InstitutionMemberDTO();
        member.setPerson(memberDTO);

        when(assignmentRepository.personHasAnyProfileInInstitution(memberDTO.getId(), institution.getId()))
                .thenReturn(false);
        when(profileService.createOrGetOrganizationMemberProfile(institution)).thenReturn(memberProfile);
        when(profileMapper.convert(memberProfile)).thenReturn(memberProfileDTO);

        boolean result = organizationMembersService.removeProfileFromMember(institution, member, memberProfileDTO);

        assertThat(result).isTrue();
        verify(personProfileAssignmentService, times(1)).remove(memberDTO, memberProfileDTO);
        verify(personProfileAssignmentService, times(1)).assign(memberDTO, memberProfileDTO);
    }

    @Test
    void removeProfileFromMember_shouldNotRemove_whenMemberIsLastManager() {
        ProfileDTO managerProfileDTO = new ProfileDTO();
        managerProfileDTO.setId(101L);
        managerProfileDTO.setCode(ProfileConstants.ORGANIZATION_MANAGER);
        managerProfileDTO.setScope(PermissionScopeType.ORGANISATION);

        InstitutionMemberDTO member = new InstitutionMemberDTO();
        member.setPerson(memberDTO);

        when(personProfileAssignmentService.isNotLastOrganizationManager(institution, memberDTO)).thenReturn(false);

        boolean result = organizationMembersService.removeProfileFromMember(institution, member, managerProfileDTO);

        assertThat(result).isFalse();
        verify(personProfileAssignmentService, never()).remove(any(), any());
    }

    private PersonProfileAssignment assignment(Person person, Profile profile) {
        PersonProfileAssignment assignment = new PersonProfileAssignment();
        assignment.setPerson(person);
        assignment.setProfile(profile);
        return assignment;
    }
}
