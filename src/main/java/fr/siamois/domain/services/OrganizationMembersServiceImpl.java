package fr.siamois.domain.services;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.InstitutionMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.infrastructure.database.repositories.permissions.ProfileRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Primary
@RequiredArgsConstructor
public class OrganizationMembersServiceImpl implements OrganizationMembersServiceInterface {

    private final PersonProfileAssignmentRepository assignmentRepository;
    private final PersonMapper personMapper;
    private final ProfileMapper profileMapper;
    private final ProfileRepository profileRepository;
    private final PersonProfileAssignmentService personProfileAssignmentService;

    @Override
    public List<InstitutionMemberDTO> findMembersOf(@NonNull InstitutionDTO institution) {
        Map<Person, Set<ProfileDTO>> profilesByPerson = new HashMap<>();

        for (PersonProfileAssignment personProfileAssignment : assignmentRepository.findAllAssignmentsByInstitutionId(institution.getId())) {
            if (!profilesByPerson.containsKey(personProfileAssignment.getPerson())) {
                profilesByPerson.put(personProfileAssignment.getPerson(), new HashSet<>());
            }
            profilesByPerson.get(personProfileAssignment.getPerson()).add(profileMapper.convert(personProfileAssignment.getProfile()));
        }

        return profilesByPerson.keySet()
                .stream()
                .map((person) -> {
                    InstitutionMemberDTO dto = new InstitutionMemberDTO();
                    dto.setPerson(personMapper.convert(person));
                    dto.setProfiles(new ArrayList<>(profilesByPerson.get(person)));
                    return dto;
                })
                .toList();
    }

    @Override
    public List<ProfileDTO> findAvailableProfiles(InstitutionDTO institution) {
        return profileRepository
                .findProfilesByInstitutionId(institution.getId())
                .stream()
                .map(profileMapper::convert)
                .toList();
    }

    @Override
    public InstitutionMemberDTO addMemberToInstitution(InstitutionDTO institution, PersonDTO person, List<ProfileDTO> profiles) {
        return personProfileAssignmentService.addToInstitution(institution, person, profiles);
    }
}
