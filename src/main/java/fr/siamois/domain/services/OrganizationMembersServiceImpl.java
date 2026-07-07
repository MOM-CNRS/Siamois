package fr.siamois.domain.services;

import fr.siamois.domain.models.permissions.ProfileConstants;
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

import java.util.List;

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
        return assignmentRepository
                .findAllPersonsByProfileCodeAndInstitutionId(ProfileConstants.ORGANIZATION_MEMBER, institution.getId())
                .stream()
                .map((person) -> {
                    InstitutionMemberDTO dto = new InstitutionMemberDTO();
                    List<ProfileDTO> profiles = assignmentRepository
                            .findAllProfilesOfPersonInInstitution(person.getId(), institution.getId())
                            .stream()
                            .map(profileMapper::convert)
                            .toList();
                    dto.setPerson(personMapper.convert(person));
                    dto.setProfiles(profiles);
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
        for (ProfileDTO profile : profiles) {
            personProfileAssignmentService.assignProfile(, person);
        }
    }
}
