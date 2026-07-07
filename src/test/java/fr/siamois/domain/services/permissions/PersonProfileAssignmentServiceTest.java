package fr.siamois.domain.services.permissions;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonProfileAssignmentServiceTest {

    @Mock
    private PersonProfileAssignmentRepository personProfileAssignmentRepository;

    @Mock
    private PersonMapper personMapper;

    @Mock
    private ProfileService profileService;

    @Mock
    private ProfileMapper profileMapper;

    @InjectMocks
    private PersonProfileAssignmentService service;

    private Person person;
    private PersonDTO personDTO;
    private Profile profile;
    private ProfileDTO profileDTO;
    private InstitutionDTO institutionDTO;
    private ActionUnitDTO actionUnitDTO;

    @BeforeEach
    void setUp() {
        person = new Person();
        person.setId(1L);

        personDTO = new PersonDTO();

        profile = new Profile();
        profile.setId(10L);
        profile.setCode("SOME_CODE");

        profileDTO = new ProfileDTO();
        profileDTO.setCode("SOME_CODE");

        institutionDTO = new InstitutionDTO();
        actionUnitDTO = new ActionUnitDTO();
    }

    @Test
    void addToManagers_ReturnsTrueWhenNotAlreadyAssigned() {
        when(profileService.createOrGetOrganizationManagerProfile(institutionDTO)).thenReturn(profile);
        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(profile.getId(), person.getId()))
                .thenReturn(Optional.empty());

        boolean result = service.addToManagers(institutionDTO, personDTO);

        assertTrue(result);
        verify(personProfileAssignmentRepository, times(1)).save(any(PersonProfileAssignment.class));
    }

    @Test
    void addToManagers_ReturnsFalseWhenAlreadyAssigned() {
        when(profileService.createOrGetOrganizationManagerProfile(institutionDTO)).thenReturn(profile);
        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(profile.getId(), person.getId()))
                .thenReturn(Optional.of(new PersonProfileAssignment()));

        boolean result = service.addToManagers(institutionDTO, personDTO);

        assertFalse(result);
        verify(personProfileAssignmentRepository, never()).save(any());
    }

    @Test
    void addToActionManagers_ReturnsTrueWhenNotAlreadyAssigned() {
        when(profileService.createOrGetOrganizationProjectManagerProfile(institutionDTO)).thenReturn(profile);
        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(profile.getId(), person.getId()))
                .thenReturn(Optional.empty());

        boolean result = service.addToActionManagers(institutionDTO, personDTO);

        assertTrue(result);
        verify(personProfileAssignmentRepository, times(1)).save(any(PersonProfileAssignment.class));
    }

    @Test
    void addToProjectMembers_ReturnsTrueWhenNotAlreadyAssigned() {
        when(profileService.createOrGetProjectMemberProfile(actionUnitDTO)).thenReturn(profile);
        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(profile.getId(), person.getId()))
                .thenReturn(Optional.empty());

        boolean result = service.addToProjectMembers(actionUnitDTO, personDTO);

        assertTrue(result);
        verify(personProfileAssignmentRepository, times(1)).save(any(PersonProfileAssignment.class));
    }

    @Test
    void addToInstitution_AddsDefaultMemberProfileIfMissing() {
        List<ProfileDTO> profiles = new ArrayList<>();
        profiles.add(profileDTO); // Ne contient pas ORGANIZATION_MEMBER

        Profile memberProfile = new Profile();
        memberProfile.setId(99L);

        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(profileService.createOrGetOrganizationMemberProfile(institutionDTO)).thenReturn(memberProfile);
        when(profileMapper.invertConvert(profileDTO)).thenReturn(profile);

        // Simuler qu'aucune assignation n'existe déjà
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(person.getId())))
                .thenReturn(Optional.empty());

        InstitutionMemberDTO result = service.addToInstitution(institutionDTO, personDTO, profiles);

        assertNotNull(result);
        assertEquals(personDTO, result.getPerson());
        assertEquals(profiles, result.getProfiles());

        // Vérifie que createOrGetOrganizationMemberProfile a été appelé car le code n'y était pas
        verify(profileService, times(1)).createOrGetOrganizationMemberProfile(institutionDTO);
        // Vérifie qu'on sauvegarde 2 assignations : le membre par défaut + le profil passé en liste
        verify(personProfileAssignmentRepository, times(2)).save(any(PersonProfileAssignment.class));
    }

    @Test
    void addToInstitution_SkipsDefaultMemberProfileIfAlreadyPresent() {
        ProfileDTO orgMemberProfileDTO = new ProfileDTO();
        orgMemberProfileDTO.setCode(ProfileConstants.ORGANIZATION_MEMBER);

        List<ProfileDTO> profiles = new ArrayList<>();
        profiles.add(orgMemberProfileDTO); // Contient ORGANIZATION_MEMBER

        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(profileMapper.invertConvert(orgMemberProfileDTO)).thenReturn(profile);
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(person.getId())))
                .thenReturn(Optional.empty());

        InstitutionMemberDTO result = service.addToInstitution(institutionDTO, personDTO, profiles);

        assertNotNull(result);

        // Vérifie que createOrGetOrganizationMemberProfile n'a pas été appelé
        verify(profileService, never()).createOrGetOrganizationMemberProfile(any());
        // Sauvegarde d'une seule assignation
        verify(personProfileAssignmentRepository, times(1)).save(any(PersonProfileAssignment.class));
    }

    @Test
    void testAddToProjectMembers_WithList_AddsDefaultMemberProfileIfMissing() {
        List<ProfileDTO> profiles = new ArrayList<>();
        profiles.add(profileDTO); // Ne contient pas PROJECT_MEMBER

        Profile memberProfile = new Profile();
        memberProfile.setId(88L);

        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(profileService.createOrGetProjectMemberProfile(actionUnitDTO)).thenReturn(memberProfile);
        when(profileMapper.invertConvert(profileDTO)).thenReturn(profile);
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(person.getId())))
                .thenReturn(Optional.empty());

        ProjectMemberDTO result = service.addToProjectMembers(actionUnitDTO, personDTO, profiles);

        assertNotNull(result);
        assertEquals(personDTO, result.getPerson());

        verify(profileService, times(1)).createOrGetProjectMemberProfile(actionUnitDTO);
        verify(personProfileAssignmentRepository, times(2)).save(any(PersonProfileAssignment.class));
    }

    @Test
    void addToInstance_AssignsAllProfilesProvided() {
        List<ProfileDTO> profiles = new ArrayList<>();
        profiles.add(profileDTO);

        ProfileDTO secondProfileDTO = new ProfileDTO();
        secondProfileDTO.setCode("ANOTHER_CODE");
        profiles.add(secondProfileDTO);

        Profile secondProfile = new Profile();
        secondProfile.setId(11L);

        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(profileMapper.invertConvert(profileDTO)).thenReturn(profile);
        when(profileMapper.invertConvert(secondProfileDTO)).thenReturn(secondProfile);
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(person.getId())))
                .thenReturn(Optional.empty());

        ApplicationMemberDTO result = service.addToInstance(personDTO, profiles);

        assertNotNull(result);
        assertEquals(personDTO, result.getPerson());
        assertEquals(profiles, result.getProfiles());

        // Vérifie qu'on sauvegarde autant d'assignations qu'il y a de profils dans la liste
        verify(personProfileAssignmentRepository, times(2)).save(any(PersonProfileAssignment.class));
    }
}
