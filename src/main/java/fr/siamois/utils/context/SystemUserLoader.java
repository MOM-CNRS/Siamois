package fr.siamois.utils.context;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemUserLoader {

    private final PersonRepository personRepository;
    private final InstitutionRepository institutionRepository;
    private final PersonMapper personMapper;
    private final InstitutionMapper institutionMapper;


    @Transactional
    public UserInfo loadSystemUser() {

        // --- Personne système ---
        PersonDTO admin = personMapper.convert(personRepository.findByUsernameIgnoreCase("system")
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
                }));

        // --- Institution par défaut ---
        InstitutionDTO defaultInsti = institutionMapper.convert(institutionRepository.findInstitutionByIdentifier("siamois")
                .orElseGet(() -> {
                    Institution inst = new Institution();
                    inst.setName("Organisation par défaut");
                    inst.setDescription("DEFAULT");
                    inst.setIdentifier("siamois");
                    return institutionRepository.save(inst);
                }));

        return new UserInfo(defaultInsti, admin, "en");
    }
}


