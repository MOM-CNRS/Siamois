package fr.siamois.domain.services;

import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.dto.entity.ProjectMemberDTO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
public class ProjectMembersServiceInterfaceImpl implements ProjectMembersServiceInterface {
    @Override
    public List<ProjectMemberDTO> findMembersOf(ActionUnitDTO project) {
        return List.of();
    }

    @Override
    public List<ProfileDTO> findAvailableProfiles(ActionUnitDTO project) {
        return List.of();
    }

    @Override
    public ProjectMemberDTO addMemberToProject(ActionUnitDTO project, PersonDTO person, List<ProfileDTO> profiles) {
        return null;
    }
}
