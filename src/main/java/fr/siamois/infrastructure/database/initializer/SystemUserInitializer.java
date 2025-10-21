package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class SystemUserInitializer implements DatabaseInitializer {

    private final PersonRepository personRepository;
    private final InstitutionRepository institutionRepository;

    @Override
    public void initialize() throws DatabaseDataInitException {
        initializeSystemUser();
        initializeAdminOrganization();
    }

    private void initializeSystemUser() {
        Optional<Person> result =  personRepository.findByUsernameIgnoreCase("system");
        if (result.isEmpty()) {
            Person person = new Person();
            person.setUsername("system");
            person.setEnabled(true);
            person.setName("SIAMOIS");
            person.setLastname("SYSTEM");
            person.setEmail("system@siamois.fr");
            person.setPassword("SIAMOIS_UNHASHED");
            person.setSuperAdmin(true);
            personRepository.save(person);
        }
    }

    void initializeAdminOrganization() {
        if (processExistingInstitution()) return;

        Institution institution = new Institution();
        institution.setName("Organisation par défaut");
        institution.setDescription("DEFAULT");
        institution.setIdentifier("siamois");

        institutionRepository.save(institution);

        log.info("Created institution {}", institution.getIdentifier());
    }

    protected boolean processExistingInstitution() {
        Optional<Institution> optInstitution = institutionRepository.findInstitutionByIdentifier("siamois");
        return optInstitution.isPresent();
    }

}
