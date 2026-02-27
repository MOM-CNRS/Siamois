package fr.siamois.utils.context;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.dto.entity.ConceptLabelDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemUserLoader {

    private final PersonRepository personRepository;
    private final InstitutionRepository institutionRepository;
    private final ConversionService conversionService;


    @Transactional(readOnly = true)
    public UserInfo loadSystemUser() {

        // --- Personne système ---
        PersonDTO admin = conversionService.convert(personRepository.findByUsernameIgnoreCase("system")
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
                }), PersonDTO.class);

        // --- Institution par défaut ---
        InstitutionDTO defaultInsti = conversionService.convert(institutionRepository.findInstitutionByIdentifier("siamois")
                .orElseGet(() -> {
                    Institution inst = new Institution();
                    inst.setName("Organisation par défaut");
                    inst.setDescription("DEFAULT");
                    inst.setIdentifier("siamois");
                    return institutionRepository.save(inst);
                }),InstitutionDTO.class);

        return new UserInfo(defaultInsti, admin, "en");
    }
}


