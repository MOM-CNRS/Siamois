package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.initializer.seeder.InstitutionSeeder;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@Getter
@Setter
@Order(1)
public class AdminInitializer implements DatabaseInitializer {

    private final BCryptPasswordEncoder passwordEncoder;
    private final PersonRepository personRepository;
    private final InstitutionRepository institutionRepository;
    private final ApplicationContext applicationContext;
    private final InstitutionSeeder institutionSeeder;

    @Value("${siamois.admin.username}")
    private String adminUsername;

    @Value("${siamois.admin.password}")
    private String adminPassword;

    @Value("${siamois.admin.email}")
    private String adminEmail;

    private Person createdAdmin;
    private Institution createdInstitution;

    public AdminInitializer(BCryptPasswordEncoder passwordEncoder,
                            PersonRepository personRepository,
                            InstitutionRepository institutionRepository,
                            ApplicationContext applicationContext, InstitutionSeeder institutionSeeder) {
        this.passwordEncoder = passwordEncoder;
        this.personRepository = personRepository;
        this.institutionRepository = institutionRepository;
        this.applicationContext = applicationContext;
        this.institutionSeeder = institutionSeeder;
    }

    /**
     * Marks all previous person with super admin flag as FALSE if username is different then adminUsername.
     */
    @Override
    @Transactional
    public void initialize() throws DatabaseDataInitException {
        initializeAdmin();
        initializeAdminOrganization();
    }

    void initializeAdmin() throws DatabaseDataInitException {
        if (processExistingAdmins()) return;

        Person person = new Person();
        person.setUsername(adminUsername);
        person.setPassword(passwordEncoder.encode(adminPassword));
        person.setEmail(adminEmail);
        person.setName("Admin");
        person.setLastname("Admin");
        person.setSuperAdmin(true);
        person.setEnabled(true);

        try {
            createdAdmin = personRepository.save(person);
            log.info("Created admin: {}", createdAdmin.getUsername());
        } catch (DataIntegrityViolationException e) {
            log.error("User with username {} already exists and is not SUPER ADMIN but is supposed to. " +
                    "Check the database manually", adminUsername, e);
            throw new DatabaseDataInitException("Super admin account started wrongly.", e);
        }
    }

    /*
     * @return True if wanted admin already exist, false otherwise
     */
    private boolean processExistingAdmins() {
        List<Person> admins = personRepository.findAllSuperAdmin();
        Person adminWithUsername = null;
        for (Person admin : admins) {
            if (isNotAskedAdmin(admin)) {
                admin.setSuperAdmin(false);
                personRepository.save(admin);
            } else {
                adminWithUsername = admin;
            }
        }

        if (adminWithUsername != null) {
            createdAdmin = adminWithUsername;
            log.debug("Super admin already exists: {}", createdAdmin.getUsername());
            return true;
        }
        return false;
    }

    private boolean isNotAskedAdmin(Person admin) {
        return !admin.getUsername().equalsIgnoreCase(adminUsername);
    }

    /**
     * Creates the Siamois Administration organisation if it doesn't exist. Changes the manager of the organisation
     * to the current admin
     */
    void initializeAdminOrganization() throws DatabaseDataInitException {

        InstitutionSeeder.InstitutionSpec inst = new InstitutionSeeder.InstitutionSpec(
                "Organisation par défaut",
                "DEFAULT",
                "siamois",
                List.of(createdAdmin.getEmail()),
                "https://thesaurus.mom.fr", "th230"
        );
        
        institutionSeeder.seed(List.of(inst));

        log.info("Created institution {}","siamois");
    }


}
