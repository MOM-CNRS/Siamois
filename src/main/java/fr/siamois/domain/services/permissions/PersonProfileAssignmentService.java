package fr.siamois.domain.services.permissions;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PersonProfileAssignmentService {

    private final PersonProfileAssignmentRepository personProfileAssignmentRepository;
    private final PersonMapper personMapper;
    private final ProfileService profileService;
    private final ProfileMapper profileMapper;

    private void assignProfile(@NonNull Profile profile, @NonNull Person person) {
        Optional<PersonProfileAssignment> opt = personProfileAssignmentRepository.findByProfileIdAndPersonId(profile.getId(), person.getId());
        if (opt.isPresent()) return;
        PersonProfileAssignment assignment = new PersonProfileAssignment();
        assignment.setProfile(profile);
        assignment.setPerson(person);
        personProfileAssignmentRepository.save(assignment);
    }

    public boolean addToManagers(InstitutionDTO institution, PersonDTO person) {
        Profile organizationManagers = profileService.createOrGetOrganizationManagerProfile(institution);
        Profile organizationMember = profileService.createOrGetOrganizationMemberProfile(institution);
        List<ProfileDTO> profiles = new ArrayList<>();
        profiles.add(profileMapper.convert(organizationManagers));
        profiles.add(profileMapper.convert(organizationMember));
        return addToInstitution(institution, person, profiles) != null;
    }

    public boolean addToProjectMembers(ActionUnitDTO actionUnit, PersonDTO person) {
        return addToProjectMembers(actionUnit, person, List.of()) != null;
    }

    public InstitutionMemberDTO addToInstitution(InstitutionDTO institution, PersonDTO person, List<ProfileDTO> profiles) {
        int i = 0;
        Set<ProfileDTO> profileSet = new HashSet<>();
        Person personToAdd = personMapper.invertConvert(person);
        while (i < profiles.size() && !profiles.get(i).getCode().equals(ProfileConstants.ORGANIZATION_MEMBER)) i++;
        if (i == profiles.size()) {
            Profile member = profileService.createOrGetOrganizationMemberProfile(institution);
            assignProfile(member, personToAdd);
            profileSet.add(profileMapper.convert(member));
        }

        for (ProfileDTO profile : profiles) {
            Profile currentProfile = profileMapper.invertConvert(profile);
            assignProfile(currentProfile, personToAdd);
            profileSet.add(profileMapper.convert(currentProfile));
        }

        InstitutionMemberDTO institutionMemberDTO = new InstitutionMemberDTO();
        institutionMemberDTO.setPerson(person);
        institutionMemberDTO.setProfiles(new ArrayList<>(profileSet));
        return institutionMemberDTO;
    }

    public ProjectMemberDTO addToProjectMembers(ActionUnitDTO project, PersonDTO person, List<ProfileDTO> profiles) {
        int i = 0;
        Set<ProfileDTO> profileSet = new HashSet<>();
        Person personToAdd = personMapper.invertConvert(person);
        while (i < profiles.size() && !profiles.get(i).getCode().equals(ProfileConstants.PROJECT_MEMBER)) i++;
        if (i == profiles.size()) {
            Profile member = profileService.createOrGetProjectMemberProfile(project);
            assignProfile(member, personToAdd);
            profileSet.add(profileMapper.convert(member));
        }

        Profile institutionMemberProfile = profileService.createOrGetOrganizationMemberProfile(project.getCreatedByInstitution());
        assignProfile(institutionMemberProfile, personToAdd);

        for (ProfileDTO profile : profiles) {
            Profile currentProfile = profileMapper.invertConvert(profile);
            assignProfile(currentProfile, personToAdd);
            profileSet.add(profileMapper.convert(currentProfile));
        }

        ProjectMemberDTO projectMemberDTO = new ProjectMemberDTO();
        projectMemberDTO.setPerson(person);
        projectMemberDTO.setProfiles(new ArrayList<>(profileSet));
        return projectMemberDTO;
    }

    public ApplicationMemberDTO addToInstance(PersonDTO person, List<ProfileDTO> profiles) {
        Person personToAdd = personMapper.invertConvert(person);
        for (ProfileDTO profile : profiles) {
            Profile currentProfile = profileMapper.invertConvert(profile);
            assignProfile(currentProfile, personToAdd);
        }
        ApplicationMemberDTO applicationMemberDTO = new ApplicationMemberDTO();
        applicationMemberDTO.setPerson(person);
        applicationMemberDTO.setProfiles(profiles);
        return applicationMemberDTO;
    }

    public boolean isNotSuperAdmin(PersonDTO person) {
        return !personProfileAssignmentRepository.personIsSuperAdmin(person.getId());
    }

    public void assign(PersonDTO person, ProfileDTO profile) {
        Profile currentProfile = profileMapper.invertConvert(profile);
        Person personToAssign = personMapper.invertConvert(person);
        assignProfile(currentProfile, personToAssign);
    }

    public void remove(PersonDTO person, ProfileDTO profile) {
        Optional<PersonProfileAssignment> ppaOpt = personProfileAssignmentRepository.findByProfileIdAndPersonId(profile.getId(), person.getId());
        ppaOpt.ifPresent(personProfileAssignmentRepository::delete);
    }
}
