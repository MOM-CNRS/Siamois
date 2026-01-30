package fr.siamois.utils.context;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import org.springframework.stereotype.Service;
@Service
public class SystemUserLoader {

    private final PersonRepository personRepository;
    private final InstitutionRepository institutionRepository;

    public SystemUserLoader(PersonRepository personRepository,
                            InstitutionRepository institutionRepository) {
        this.personRepository = personRepository;
        this.institutionRepository = institutionRepository;
    }

    public UserInfo loadSystemUser() {

        // --- Personne système ---
        Person admin = personRepository.findByUsernameIgnoreCase("system")
                .orElseGet(() -> {
                    Person p = new Person();
                    p.setUsername("system");
                    p.setEnabled(true);
                    p.setName("SIAMOIS");
                    p.setLastname("SYSTEM");
                    p.setEmail("system@siamois.fr");
                    p.setPassword("SIAMOIS_UNHASHED");
                    p.setSuperAdmin(false);
                    return personRepository.save(p);
                });

        // --- Institution par défaut ---
        Institution defaultInsti = institutionRepository.findInstitutionByIdentifier("siamois")
                .orElseGet(() -> {
                    Institution inst = new Institution();
                    inst.setName("Organisation par défaut");
                    inst.setDescription("DEFAULT");
                    inst.setIdentifier("siamois");
                    return institutionRepository.save(inst);
                });

        return new UserInfo(defaultInsti, admin, "en");
    }
}


