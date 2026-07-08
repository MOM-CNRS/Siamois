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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        actionUnitDTO.setCreatedByInstitution(institutionDTO);
    }

    private Profile buildProfile(Long id, String code) {
        Profile p = new Profile();
        p.setId(id);
        p.setCode(code);
        return p;
    }

    private ProfileDTO buildProfileDTO(String code) {
        ProfileDTO dto = new ProfileDTO();
        dto.setCode(code);
        return dto;
    }

    @Test
    void addToManagers_AssignsManagerAndMemberProfiles() {
        Profile managerProfile = buildProfile(10L, ProfileConstants.ORGANIZATION_MANAGER);
        Profile memberProfile = buildProfile(11L, ProfileConstants.ORGANIZATION_MEMBER);
        ProfileDTO managerDTO = buildProfileDTO(ProfileConstants.ORGANIZATION_MANAGER);
        ProfileDTO memberDTO = buildProfileDTO(ProfileConstants.ORGANIZATION_MEMBER);

        when(profileService.createOrGetOrganizationManagerProfile(institutionDTO)).thenReturn(managerProfile);
        when(profileService.createOrGetOrganizationMemberProfile(institutionDTO)).thenReturn(memberProfile);
        when(profileMapper.convert(managerProfile)).thenReturn(managerDTO);
        when(profileMapper.convert(memberProfile)).thenReturn(memberDTO);
        when(profileMapper.invertConvert(managerDTO)).thenReturn(managerProfile);
        when(profileMapper.invertConvert(memberDTO)).thenReturn(memberProfile);
        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(person.getId())))
                .thenReturn(Optional.empty());

        boolean result = service.addToManagers(institutionDTO, personDTO);

        assertTrue(result);
        verify(personProfileAssignmentRepository, times(2)).save(any(PersonProfileAssignment.class));
    }

    @Test
    void addToManagers_DoesNotSaveWhenAlreadyAssigned() {
        Profile managerProfile = buildProfile(10L, ProfileConstants.ORGANIZATION_MANAGER);
        Profile memberProfile = buildProfile(11L, ProfileConstants.ORGANIZATION_MEMBER);
        ProfileDTO managerDTO = buildProfileDTO(ProfileConstants.ORGANIZATION_MANAGER);
        ProfileDTO memberDTO = buildProfileDTO(ProfileConstants.ORGANIZATION_MEMBER);

        when(profileService.createOrGetOrganizationManagerProfile(institutionDTO)).thenReturn(managerProfile);
        when(profileService.createOrGetOrganizationMemberProfile(institutionDTO)).thenReturn(memberProfile);
        when(profileMapper.convert(managerProfile)).thenReturn(managerDTO);
        when(profileMapper.convert(memberProfile)).thenReturn(memberDTO);
        when(profileMapper.invertConvert(managerDTO)).thenReturn(managerProfile);
        when(profileMapper.invertConvert(memberDTO)).thenReturn(memberProfile);
        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(person.getId())))
                .thenReturn(Optional.of(new PersonProfileAssignment()));

        boolean result = service.addToManagers(institutionDTO, personDTO);

        assertTrue(result);
        verify(personProfileAssignmentRepository, never()).save(any());
    }

    @Test
    void addToProjectMembers_ReturnsTrueWhenNotAlreadyAssigned() {
        Profile orgMemberProfile = buildProfile(50L, ProfileConstants.ORGANIZATION_MEMBER);

        when(profileService.createOrGetProjectMemberProfile(actionUnitDTO)).thenReturn(profile);
        when(profileService.createOrGetOrganizationMemberProfile(institutionDTO)).thenReturn(orgMemberProfile);
        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(person.getId())))
                .thenReturn(Optional.empty());

        boolean result = service.addToProjectMembers(actionUnitDTO, personDTO);

        assertTrue(result);
        // Une assignation pour le profil membre de projet + une pour le profil membre d'organisation
        verify(personProfileAssignmentRepository, times(2)).save(any(PersonProfileAssignment.class));
    }

    @Test
    void addToProjectMembers_AssignsOrganizationMemberProfileOfProjectInstitution() {
        Profile orgMemberProfile = buildProfile(50L, ProfileConstants.ORGANIZATION_MEMBER);

        when(profileService.createOrGetProjectMemberProfile(actionUnitDTO)).thenReturn(profile);
        when(profileService.createOrGetOrganizationMemberProfile(institutionDTO)).thenReturn(orgMemberProfile);
        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(person.getId())))
                .thenReturn(Optional.empty());

        service.addToProjectMembers(actionUnitDTO, personDTO);

        verify(profileService).createOrGetOrganizationMemberProfile(institutionDTO);
        ArgumentCaptor<PersonProfileAssignment> assignmentCaptor = ArgumentCaptor.forClass(PersonProfileAssignment.class);
        verify(personProfileAssignmentRepository, times(2)).save(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getAllValues())
                .extracting(PersonProfileAssignment::getProfile)
                .containsExactlyInAnyOrder(profile, orgMemberProfile);
    }

    @Test
    void addToInstitution_AddsDefaultMemberProfileIfMissing() {
        List<ProfileDTO> profiles = new ArrayList<>();
        profiles.add(profileDTO); // Ne contient pas ORGANIZATION_MEMBER

        Profile memberProfile = buildProfile(99L, ProfileConstants.ORGANIZATION_MEMBER);
        ProfileDTO memberDTO = buildProfileDTO(ProfileConstants.ORGANIZATION_MEMBER);

        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(profileService.createOrGetOrganizationMemberProfile(institutionDTO)).thenReturn(memberProfile);
        when(profileMapper.invertConvert(profileDTO)).thenReturn(profile);
        when(profileMapper.convert(memberProfile)).thenReturn(memberDTO);
        when(profileMapper.convert(profile)).thenReturn(profileDTO);

        // Simuler qu'aucune assignation n'existe déjà
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(person.getId())))
                .thenReturn(Optional.empty());

        InstitutionMemberDTO result = service.addToInstitution(institutionDTO, personDTO, profiles);

        assertNotNull(result);
        assertEquals(personDTO, result.getPerson());
        // Le profil membre ajouté par défaut fait désormais partie des profils retournés
        assertThat(result.getProfiles()).containsExactlyInAnyOrder(profileDTO, memberDTO);

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

        Profile orgMemberProfile = buildProfile(50L, ProfileConstants.ORGANIZATION_MEMBER);

        when(personMapper.invertConvert(personDTO)).thenReturn(person);
        when(profileService.createOrGetProjectMemberProfile(actionUnitDTO)).thenReturn(memberProfile);
        when(profileService.createOrGetOrganizationMemberProfile(institutionDTO)).thenReturn(orgMemberProfile);
        when(profileMapper.invertConvert(profileDTO)).thenReturn(profile);
        when(personProfileAssignmentRepository.findByProfileIdAndPersonId(anyLong(), eq(person.getId())))
                .thenReturn(Optional.empty());

        ProjectMemberDTO result = service.addToProjectMembers(actionUnitDTO, personDTO, profiles);

        assertNotNull(result);
        assertEquals(personDTO, result.getPerson());

        verify(profileService, times(1)).createOrGetProjectMemberProfile(actionUnitDTO);
        // Membre de projet par défaut + membre d'organisation + le profil passé en liste
        verify(personProfileAssignmentRepository, times(3)).save(any(PersonProfileAssignment.class));
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
