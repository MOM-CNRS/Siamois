package fr.siamois.domain.services;

import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.dto.entity.ProjectMemberDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
@RequiredArgsConstructor
public class ProjectMembersServiceInterfaceImpl implements ProjectMembersServiceInterface {

    private final PersonProfileAssignmentRepository personProfileAssignmentRepository;
    private final ProfileService profileService;
    private final PersonMapper personMapper;
    private final ProfileMapper profileMapper;
    private final PersonProfileAssignmentService personProfileAssignmentService;

    @Override
    public List<ProjectMemberDTO> findMembersOf(ActionUnitDTO project) {
        return personProfileAssignmentRepository
                .findAllPersonsByProfileActionUnitId(project.getId())
                .stream()
                .map((person) -> {
                    ProjectMemberDTO dto = new ProjectMemberDTO();
                    List<ProfileDTO> profiles = personProfileAssignmentRepository
                            .findAllProfilesOfPersonInActionUnit(person.getId(), project.getId())
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
    public List<ProfileDTO> findAvailableProfiles(ActionUnitDTO project) {
        return profileService.findAllProfilesByActionUnit(project);
    }

    @Override
    public ProjectMemberDTO addMemberToProject(ActionUnitDTO project, PersonDTO person, List<ProfileDTO> profiles) {
        return personProfileAssignmentService.addToProjectMembers(project, person, profiles);
    }
}
