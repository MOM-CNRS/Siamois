package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.initializer.seeder.InstitutionSeeder;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminInitializerTest {

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private InstitutionSeeder institutionSeeder;


    private AdminInitializer adminInitializer;

    private Institution institution;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adminInitializer = new AdminInitializer(passwordEncoder,
                personRepository,
                institutionRepository,
                applicationContext,
                institutionSeeder);
        adminInitializer.setAdminUsername("admin");
        adminInitializer.setAdminPassword("admin");
        adminInitializer.setAdminEmail("admin@example.com");

        institution = new Institution();
        institution.setId(1L);
        institution.setName("Siamois");
        institution.setIdentifier("siamois");
    }

    @Test
    void initializeAdmin_shouldCreateAdminWhenNoAdminExists() throws DatabaseDataInitException {
        when(personRepository.findAllSuperAdmin()).thenReturn(List.of());
        when(passwordEncoder.encode("admin")).thenReturn("encodedPassword");
        when(personRepository.save(any(Person.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(institutionRepository.findInstitutionByIdentifier("siamois")).thenReturn(Optional.of(institution));


        adminInitializer.initialize();

        assertNotNull(adminInitializer.getCreatedAdmin());
        assertEquals("admin", adminInitializer.getCreatedAdmin().getUsername());
        assertEquals("encodedPassword", adminInitializer.getCreatedAdmin().getPassword());
    }

    @Test
    void initializeAdmin_shouldNotCreateAdminWhenAdminExists() throws DatabaseDataInitException {
        Person existingAdmin = new Person();
        existingAdmin.setUsername("admin");
        existingAdmin.setEmail("admin@example.com");
        when(personRepository.findAllSuperAdmin()).thenReturn(List.of(existingAdmin));
        when(institutionRepository.findInstitutionByIdentifier("siamois")).thenReturn(Optional.of(institution));

        adminInitializer.initialize();

        assertEquals(existingAdmin, adminInitializer.getCreatedAdmin());
    }

    @Test
    void initializeAdmin_shouldThrowExceptionWhenAdminUsernameExistsButIsNotAdmin() {
        when(personRepository.findAllSuperAdmin()).thenReturn(List.of());
        when(personRepository.save(any(Person.class))).thenThrow(DataIntegrityViolationException.class);

        assertThrows(DatabaseDataInitException.class, () -> adminInitializer.initializeAdmin());
    }
}