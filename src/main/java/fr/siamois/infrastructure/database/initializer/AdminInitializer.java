package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@Getter
@Setter
@Order(-10)
public class AdminInitializer implements DatabaseInitializer {

    private final BCryptPasswordEncoder passwordEncoder;
    private final PersonRepository personRepository;

    @Value("${siamois.admin.username}")
    private String adminUsername;

    @Value("${siamois.admin.password}")
    private String adminPassword;

    @Value("${siamois.admin.email}")
    private String adminEmail;

    private Person createdAdmin;

    public AdminInitializer(BCryptPasswordEncoder passwordEncoder,
                            PersonRepository personRepository) {
        this.passwordEncoder = passwordEncoder;
        this.personRepository = personRepository;
    }

    /**
     * Creates the admin account if no person with the configured admin username exists.
     * The SUPERADMIN profile is assigned later by {@link SystemPermissionsInitializer}.
     */
    @Override
    @Transactional
    public void initialize() throws DatabaseDataInitException {
        initializeAdmin();
    }

    void initializeAdmin() throws DatabaseDataInitException {
        if (processExistingAdmins()) return;

        Person person = new Person();
        person.setUsername(adminUsername);
        person.setPassword(passwordEncoder.encode(adminPassword));
        person.setEmail(adminEmail);
        person.setName("Admin");
        person.setLastname("Admin");
        person.setEnabled(true);

        try {
            createdAdmin = personRepository.save(person);
            log.info("Created admin: {}", createdAdmin.getUsername());
        } catch (DataIntegrityViolationException e) {
            log.error("Could not create the admin account with username {}. " +
                    "Check the database manually", adminUsername, e);
            throw new DatabaseDataInitException("Super admin account started wrongly.", e);
        }
    }

    /*
     * @return True if wanted admin already exist, false otherwise
     */
    private boolean processExistingAdmins() {
        Optional<Person> existingAdmin = personRepository.findByUsernameIgnoreCase(adminUsername);
        if (existingAdmin.isPresent()) {
            createdAdmin = existingAdmin.get();
            log.debug("Admin already exists: {}", createdAdmin.getUsername());
            return true;
        }
        return false;
    }

}
