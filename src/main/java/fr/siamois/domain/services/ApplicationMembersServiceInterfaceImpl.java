package fr.siamois.domain.services;

import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.dto.entity.ApplicationMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.mapper.PersonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
@RequiredArgsConstructor
public class ApplicationMembersServiceInterfaceImpl implements ApplicationMembersServiceInterface {
    private final PersonProfileAssignmentRepository personProfileAssignmentRepository;
    private final ProfileService profileService;
    private final PersonMapper personMapper;
    private final PersonProfileAssignmentService personProfileAssignmentService;

    @Override
    public List<ApplicationMemberDTO> findMembers() {
        return personProfileAssignmentRepository
                .findAllPersonsByProfileOfInstance()
                .stream()
                .map((person) -> {
                    ApplicationMemberDTO dto = new ApplicationMemberDTO();
                    dto.setPerson(personMapper.convert(person));
                    dto.setProfiles(profileService.findAllProfilesOfPersonInInstance(person));
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
}
