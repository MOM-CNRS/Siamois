package fr.siamois.domain.services;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
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

import java.util.*;

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
        Map<Person, Set<ProfileDTO>> profilesByPerson = new HashMap<>();


        for (PersonProfileAssignment personProfileAssignment : personProfileAssignmentRepository.findAllAssignmentsByActionUnitId(project.getId())) {
            if (!profilesByPerson.containsKey(personProfileAssignment.getPerson())) {
                profilesByPerson.put(personProfileAssignment.getPerson(), new HashSet<>());
            }
            profilesByPerson.get(personProfileAssignment.getPerson()).add(profileMapper.convert(personProfileAssignment.getProfile()));
        }

        return profilesByPerson.keySet()
                .stream()
                .map(person -> {
                    ProjectMemberDTO dto = new ProjectMemberDTO();
                    dto.setPerson(personMapper.convert(person));
                    dto.setProfiles(new ArrayList<>(profilesByPerson.get(person)));
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
        // TODO: Only the project admins can add members
        return personProfileAssignmentService.addToProjectMembers(project, person, profiles);
    }

    @Override
    public void removeMemberFromProject(ActionUnitDTO project, ProjectMemberDTO member) {
        // TODO : implement
        // Cannot remove the last Admin of the project.
        // Only the project admins car remove members
    }

    @Override
    public void addProfileToMember(ActionUnitDTO project, ProjectMemberDTO member, ProfileDTO profile) {
        // TODO : implement
        // only project admin can assign profiles

    }

    @Override
    public void removeProfileFromMember(ActionUnitDTO project, ProjectMemberDTO member, ProfileDTO profile) {
        // TODO : implement
        // only project admin can unassign profiles
        // the project profile cannot be removed from the last project admin, otherwise no one will be the admin
    }
}
