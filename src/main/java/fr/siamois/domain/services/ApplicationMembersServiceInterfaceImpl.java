package fr.siamois.domain.services;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.dto.entity.ApplicationMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Primary
@RequiredArgsConstructor
public class ApplicationMembersServiceInterfaceImpl implements ApplicationMembersServiceInterface {
    private final PersonProfileAssignmentRepository personProfileAssignmentRepository;
    private final PersonRepository personRepository;
    private final ProfileService profileService;
    private final PersonMapper personMapper;
    private final ProfileMapper profileMapper;
    private final PersonProfileAssignmentService personProfileAssignmentService;

    /**
     * Seeds the result with every person in the database (so members without any instance-level
     * profile still appear, with an empty profile list) before overlaying the actual assignments.
     */
    @Override
    public List<ApplicationMemberDTO> findMembers() {
        Map<Person, Set<ProfileDTO>> profilesByPerson = new HashMap<>();

        for (Person person : personRepository.findAll()) {
            profilesByPerson.put(person, new HashSet<>());
        }

        for (PersonProfileAssignment personProfileAssignment : personProfileAssignmentRepository.findAllInstanceAssignments()) {
            profilesByPerson
                    .computeIfAbsent(personProfileAssignment.getPerson(), p -> new HashSet<>())
                    .add(profileMapper.convert(personProfileAssignment.getProfile()));
        }

        return profilesByPerson.keySet()
                .stream()
                .map(person -> {
                    ApplicationMemberDTO dto = new ApplicationMemberDTO();
                    dto.setPerson(personMapper.convert(person));
                    dto.setProfiles(new ArrayList<>(profilesByPerson.get(person)));
                    return dto;
                })
                .toList();
    }

    @Override
    public List<ProfileDTO> findAvailableProfiles() {
        return profileService.findAllProfilesOfInstance();
    }

    @Override
    public ApplicationMemberDTO addMember(PersonDTO person, List<ProfileDTO> profiles) {
        return personProfileAssignmentService.addToInstance(person, profiles);
    }

    @Override
    public void addProfileToMember(ApplicationMemberDTO member, ProfileDTO profile) {
        personProfileAssignmentService.assign(member.getPerson(), profile);
    }

    @Override
    public boolean removeProfileFromMember(ApplicationMemberDTO member, ProfileDTO profile) {
        if (ProfileConstants.SUPERADMIN.equals(profile.getCode())
                && !personProfileAssignmentService.isNotLastSuperAdmin(member.getPerson())) {
            return false;
        }
        personProfileAssignmentService.remove(member.getPerson(), profile);
        return true;
    }
}
