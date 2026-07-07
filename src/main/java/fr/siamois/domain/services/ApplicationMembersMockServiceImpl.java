package fr.siamois.domain.services;

import fr.siamois.dto.entity.ApplicationMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory stand-in for {@link ApplicationMembersServiceInterface} until application user accounts
 * and profiles have real tables and repositories. Seeded once with a single mock super-admin account
 * and a fixed profile catalog; anything added only lives for the application's lifetime.
 */
@Service
public class ApplicationMembersMockServiceImpl implements ApplicationMembersServiceInterface {

    private static final List<ProfileDTO> AVAILABLE_PROFILES = buildAvailableProfiles();

    private final List<ApplicationMemberDTO> members = new CopyOnWriteArrayList<>(seedMembers());

    @Override
    public List<ApplicationMemberDTO> findMembers() {
        return new ArrayList<>(members);
    }

    @Override
    public List<ProfileDTO> findAvailableProfiles() {
        return AVAILABLE_PROFILES;
    }

    @Override
    public ApplicationMemberDTO addMember(PersonDTO person, List<ProfileDTO> profiles) {
        ApplicationMemberDTO member = new ApplicationMemberDTO();
        member.setPerson(person);
        member.setProfiles(new ArrayList<>(profiles));
        members.add(member);
        return member;
    }

    private static List<ApplicationMemberDTO> seedMembers() {
        ApplicationMemberDTO member = new ApplicationMemberDTO();

        PersonDTO person = new PersonDTO();
        person.setName("Admin");
        person.setLastname("Siamois");
        person.setEmail("admin@siamois.fr");
        person.setUsername("admin.siamois");
        person.setEnabled(true);
        member.setPerson(person);

        ProfileDTO profile = new ProfileDTO();
        profile.setName("Super-administrateur");
        member.setProfiles(new ArrayList<>(List.of(profile)));

        List<ApplicationMemberDTO> seed = new ArrayList<>();
        seed.add(member);
        return seed;
    }

    private static List<ProfileDTO> buildAvailableProfiles() {
        List<ProfileDTO> profiles = new ArrayList<>();
        for (String name : List.of("Super-administrateur", "Support", "Auditeur")) {
            ProfileDTO profile = new ProfileDTO();
            profile.setName(name);
            profiles.add(profile);
        }
        return profiles;
    }

}