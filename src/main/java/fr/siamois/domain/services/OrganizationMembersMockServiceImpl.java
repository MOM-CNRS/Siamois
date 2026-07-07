package fr.siamois.domain.services;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.InstitutionMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for {@link OrganizationMembersServiceInterface} until organization members and
 * profiles have real tables and repositories. Each institution is seeded with the same mock member on
 * first access, and shares a single fixed profile catalog; anything added only lives for the
 * application's lifetime.
 */
@Service
public class OrganizationMembersMockServiceImpl implements OrganizationMembersServiceInterface {

    private static final List<ProfileDTO> AVAILABLE_PROFILES = buildAvailableProfiles();

    private final Map<Long, List<InstitutionMemberDTO>> membersByInstitution = new ConcurrentHashMap<>();

    @Override
    public List<InstitutionMemberDTO> findMembersOf(InstitutionDTO institution) {
        return new ArrayList<>(membersByInstitution.computeIfAbsent(institution.getId(), id -> seedMembers()));
    }

    @Override
    public List<ProfileDTO> findAvailableProfiles(InstitutionDTO institution) {
        return AVAILABLE_PROFILES;
    }

    @Override
    public InstitutionMemberDTO addMemberToInstitution(InstitutionDTO institution, PersonDTO person, List<ProfileDTO> profiles) {
        InstitutionMemberDTO member = new InstitutionMemberDTO();
        member.setPerson(person);
        member.setProfiles(new ArrayList<>(profiles));
        membersByInstitution.computeIfAbsent(institution.getId(), id -> seedMembers()).add(member);
        return member;
    }

    private static List<InstitutionMemberDTO> seedMembers() {
        InstitutionMemberDTO member = new InstitutionMemberDTO();

        PersonDTO person = new PersonDTO();
        person.setName("Chirac");
        person.setLastname("Jacques");
        person.setEmail("jacques.chirac@siamois.fr");
        person.setUsername("jacqouillelafripouille");
        person.setEnabled(true);
        member.setPerson(person);

        ProfileDTO profile = new ProfileDTO();
        profile.setName("Super Administrateur");
        ProfileDTO profile2 = new ProfileDTO();
        profile2.setName("Profil 2");
        member.setProfiles(new ArrayList<>(List.of(profile, profile2)));

        List<InstitutionMemberDTO> seed = new ArrayList<>();
        seed.add(member);
        return seed;
    }

    private static List<ProfileDTO> buildAvailableProfiles() {
        List<ProfileDTO> profiles = new ArrayList<>();
        for (String name : List.of("Super Administrateur", "Responsable scientifique", "Contributeur", "Profil 2", "Lecture seule")) {
            ProfileDTO profile = new ProfileDTO();
            profile.setName(name);
            profiles.add(profile);
        }
        return profiles;
    }

}