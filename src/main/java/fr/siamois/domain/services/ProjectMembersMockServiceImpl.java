package fr.siamois.domain.services;

import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.dto.entity.ProjectMemberDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for {@link ProjectMembersServiceInterface} until project members and profiles
 * have real tables and repositories. Each project is seeded with the same mock member on first
 * access, and shares a single fixed profile catalog; anything added only lives for the
 * application's lifetime.
 */
@Service
public class ProjectMembersMockServiceImpl implements ProjectMembersServiceInterface {

    private static final List<ProfileDTO> AVAILABLE_PROFILES = buildAvailableProfiles();

    private final Map<Long, List<ProjectMemberDTO>> membersByProject = new ConcurrentHashMap<>();

    @Override
    public List<ProjectMemberDTO> findMembersOf(ActionUnitDTO project) {
        return new ArrayList<>(membersByProject.computeIfAbsent(project.getId(), id -> seedMembers()));
    }

    @Override
    public List<ProfileDTO> findAvailableProfiles(ActionUnitDTO project) {
        return AVAILABLE_PROFILES;
    }

    @Override
    public ProjectMemberDTO addMemberToProject(ActionUnitDTO project, PersonDTO person, List<ProfileDTO> profiles) {
        ProjectMemberDTO member = new ProjectMemberDTO();
        member.setPerson(person);
        member.setProfiles(new ArrayList<>(profiles));
        membersByProject.computeIfAbsent(project.getId(), id -> seedMembers()).add(member);
        return member;
    }

    private static List<ProjectMemberDTO> seedMembers() {
        ProjectMemberDTO member = new ProjectMemberDTO();

        PersonDTO person = new PersonDTO();
        person.setName("Camille");
        person.setLastname("Roussel");
        person.setEmail("c.roussel@mom.fr");
        person.setUsername("camille.roussel");
        person.setEnabled(true);
        member.setPerson(person);

        ProfileDTO profile = new ProfileDTO();
        profile.setName("Responsable d'opération");
        member.setProfiles(new ArrayList<>(List.of(profile)));

        List<ProjectMemberDTO> seed = new ArrayList<>();
        seed.add(member);
        return seed;
    }

    private static List<ProfileDTO> buildAvailableProfiles() {
        List<ProfileDTO> profiles = new ArrayList<>();
        for (String name : List.of("Responsable d'opération", "Fouilleur", "Spécialiste mobilier", "Gestionnaire de collection", "Lecture seule")) {
            ProfileDTO profile = new ProfileDTO();
            profile.setName(name);
            profiles.add(profile);
        }
        return profiles;
    }

}