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

    @Test
    void removeProfileFromMember_shouldRemove_whenProfileIsNotSuperAdmin() {
        ProfileDTO regularProfileDTO = new ProfileDTO();
        regularProfileDTO.setId(200L);
        regularProfileDTO.setCode("SOME_OTHER_CODE");

        ApplicationMemberDTO membertest = new ApplicationMemberDTO();
        membertest.setPerson(memberDTO);

        boolean result = applicationMembersService.removeProfileFromMember(membertest, regularProfileDTO);

        assertThat(result).isTrue();
        verify(personProfileAssignmentService, times(1)).remove(memberDTO, regularProfileDTO);
        verify(personProfileAssignmentService, never()).isNotLastSuperAdmin(any());
    }

    @Test
    void removeProfileFromMember_shouldRemove_whenSuperAdminIsNotLast() {
        ApplicationMemberDTO membertest = new ApplicationMemberDTO();
        membertest.setPerson(memberDTO);

        when(personProfileAssignmentService.isNotLastSuperAdmin(memberDTO)).thenReturn(true);

        boolean result = applicationMembersService.removeProfileFromMember(membertest, superAdminProfileDTO);

        assertThat(result).isTrue();
        verify(personProfileAssignmentService, times(1)).remove(memberDTO, superAdminProfileDTO);
    }

    @Test
    void removeProfileFromMember_shouldNotRemove_whenMemberIsLastSuperAdmin() {
        ApplicationMemberDTO membertest = new ApplicationMemberDTO();
        membertest.setPerson(memberDTO);

        when(personProfileAssignmentService.isNotLastSuperAdmin(memberDTO)).thenReturn(false);

        boolean result = applicationMembersService.removeProfileFromMember(membertest, superAdminProfileDTO);

        assertThat(result).isFalse();
        verify(personProfileAssignmentService, never()).remove(any(), any());
    }
}
