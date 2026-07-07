package fr.siamois.domain.services.permissions;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.infrastructure.database.repositories.permissions.ProfileRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PersonProfileAssignmentService {

    private final PersonProfileAssignmentRepository personProfileAssignmentRepository;
    private final PersonMapper personMapper;
    private final ProfileService profileService;
    private final ProfileMapper profileMapper;

    private boolean assignProfile(@NonNull Profile profile, @NonNull Person person) {
        Optional<PersonProfileAssignment> opt = personProfileAssignmentRepository.findByProfileIdAndPersonId(profile.getId(), person.getId());
        if (opt.isPresent()) return false;
        PersonProfileAssignment assignment = new PersonProfileAssignment();
        assignment.setProfile(profile);
        assignment.setPerson(person);
        personProfileAssignmentRepository.save(assignment);
        return true;
    }

    public boolean addToManagers(InstitutionDTO institution, PersonDTO person) {
        Profile organizationManagers = profileService.createOrGetOrganizationManagerProfile(institution);
        return assignProfile(organizationManagers, personMapper.invertConvert(person));
    }

    public boolean addToActionManagers(InstitutionDTO institution, PersonDTO person) {
        Profile projectManagers = profileService.createOrGetOrganizationProjectManagerProfile(institution);
        return assignProfile(projectManagers, personMapper.invertConvert(person));
    }

    public boolean addToProjectMembers(ActionUnitDTO actionUnit, PersonDTO person) {
        Profile projectMembers = profileService.createOrGetProjectMemberProfile(actionUnit);
        return assignProfile(projectMembers, personMapper.invertConvert(person));
    }

    public InstitutionMemberDTO addToInstitution(InstitutionDTO institution, PersonDTO person, List<ProfileDTO> profiles) {
        int i = 0;
        Person personToAdd = personMapper.invertConvert(person);
        while (i < profiles.size() && !profiles.get(i).getCode().equals(ProfileConstants.ORGANIZATION_MEMBER)) i++;
        if (i == profiles.size()) {
            Profile member = profileService.createOrGetOrganizationMemberProfile(institution);
            assignProfile(member, personToAdd);
        }

        for (ProfileDTO profile : profiles) {
            Profile currentProfile = profileMapper.invertConvert(profile);
            assignProfile(currentProfile, personToAdd);
        }

        InstitutionMemberDTO institutionMemberDTO = new InstitutionMemberDTO();
        institutionMemberDTO.setPerson(person);
        institutionMemberDTO.setProfiles(profiles);
        return institutionMemberDTO;
    }
}
