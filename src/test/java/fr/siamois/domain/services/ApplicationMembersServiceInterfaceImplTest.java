package fr.siamois.domain.services;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PermissionScopeType;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.dto.entity.ApplicationMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.mapper.PersonMapper;
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
class ApplicationMembersServiceInterfaceImplTest {

    @Mock
    private PersonProfileAssignmentRepository personProfileAssignmentRepository;
    @Mock
    private ProfileService profileService;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private PersonProfileAssignmentService personProfileAssignmentService;

    @InjectMocks
    private ApplicationMembersServiceInterfaceImpl applicationMembersService;

    private Person member;
    private PersonDTO memberDTO;
    private ProfileDTO superAdminProfileDTO;

    @BeforeEach
    void setUp() {
        member = new Person();
        member.setId(10L);
        member.setUsername("member");

        memberDTO = new PersonDTO();
        memberDTO.setId(10L);
        memberDTO.setUsername("member");

        superAdminProfileDTO = new ProfileDTO();
        superAdminProfileDTO.setId(100L);
        superAdminProfileDTO.setCode(ProfileConstants.SUPERADMIN);
        superAdminProfileDTO.setScope(PermissionScopeType.INSTANCE);
    }

    @Test
    void findMembers_shouldReturnMembersWithTheirAssignedProfiles() {
        when(personProfileAssignmentRepository.findAllPersonsByProfileOfInstance())
                .thenReturn(List.of(member));
        when(profileService.findAllProfilesOfPersonInInstance(member))
                .thenReturn(List.of(superAdminProfileDTO));
        when(personMapper.convert(member)).thenReturn(memberDTO);

        List<ApplicationMemberDTO> result = applicationMembersService.findMembers();

        assertThat(result).hasSize(1);
        ApplicationMemberDTO memberResult = result.get(0);
        assertThat(memberResult.getPerson()).isEqualTo(memberDTO);
        assertThat(memberResult.getProfiles()).containsExactly(superAdminProfileDTO);
    }

    @Test
    void findMembers_shouldReturnOneEntryPerMember() {
        Person otherMember = new Person();
        otherMember.setId(11L);
        otherMember.setUsername("other");

        PersonDTO otherMemberDTO = new PersonDTO();
        otherMemberDTO.setId(11L);
        otherMemberDTO.setUsername("other");

        when(personProfileAssignmentRepository.findAllPersonsByProfileOfInstance())
                .thenReturn(List.of(member, otherMember));
        when(profileService.findAllProfilesOfPersonInInstance(any(Person.class)))
                .thenReturn(List.of(superAdminProfileDTO));
        when(personMapper.convert(member)).thenReturn(memberDTO);
        when(personMapper.convert(otherMember)).thenReturn(otherMemberDTO);

        List<ApplicationMemberDTO> result = applicationMembersService.findMembers();

        assertThat(result)
                .hasSize(2)
                .extracting(ApplicationMemberDTO::getPerson)
                .containsExactlyInAnyOrder(memberDTO, otherMemberDTO);
    }

    @Test
    void findMembers_shouldReturnEmptyList_whenInstanceHasNoMember() {
        when(personProfileAssignmentRepository.findAllPersonsByProfileOfInstance())
                .thenReturn(List.of());

        List<ApplicationMemberDTO> result = applicationMembersService.findMembers();

        assertThat(result).isEmpty();
        verify(profileService, never()).findAllProfilesOfPersonInInstance(any(Person.class));
    }

    @Test
    void findAvailableProfiles_shouldReturnInstanceProfiles() {
        when(profileService.findAllProfilesOfInstance()).thenReturn(List.of(superAdminProfileDTO));

        List<ProfileDTO> result = applicationMembersService.findAvailableProfiles();

        assertThat(result).containsExactly(superAdminProfileDTO);
        verify(profileService, times(1)).findAllProfilesOfInstance();
    }

    @Test
    void findAvailableProfiles_shouldReturnEmptyList_whenInstanceHasNoProfile() {
        when(profileService.findAllProfilesOfInstance()).thenReturn(List.of());

        List<ProfileDTO> result = applicationMembersService.findAvailableProfiles();

        assertThat(result).isEmpty();
    }

    @Test
    void addMember_shouldDelegateToAssignmentService() {
        List<ProfileDTO> profiles = List.of(superAdminProfileDTO);

        ApplicationMemberDTO expected = new ApplicationMemberDTO();
        expected.setPerson(memberDTO);
        expected.setProfiles(profiles);

        when(personProfileAssignmentService.addToInstance(memberDTO, profiles))
                .thenReturn(expected);

        ApplicationMemberDTO result = applicationMembersService.addMember(memberDTO, profiles);

        assertThat(result).isEqualTo(expected);
        verify(personProfileAssignmentService, times(1)).addToInstance(memberDTO, profiles);
    }
}
