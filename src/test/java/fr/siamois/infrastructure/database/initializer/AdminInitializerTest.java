package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
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


    private AdminInitializer adminInitializer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adminInitializer = new AdminInitializer(passwordEncoder,
                personRepository,
                institutionRepository,
                applicationContext);
        adminInitializer.setAdminUsername("admin");
        adminInitializer.setAdminPassword("admin");
        adminInitializer.setAdminEmail("admin@example.com");
    }

    @Test
    void initializeAdmin_shouldCreateAdminWhenNoAdminExists() throws DatabaseDataInitException {
        when(personRepository.findAllSuperAdmin()).thenReturn(List.of());
        when(passwordEncoder.encode("admin")).thenReturn("encodedPassword");
        when(personRepository.save(any(Person.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminInitializer.initialize();

        assertNotNull(adminInitializer.getCreatedAdmin());
        assertEquals("admin", adminInitializer.getCreatedAdmin().getUsername());
        assertEquals("encodedPassword", adminInitializer.getCreatedAdmin().getPassword());
    }

    @Test
    void initializeAdmin_shouldNotCreateAdminWhenAdminExists() throws DatabaseDataInitException {
        Person existingAdmin = new Person();
        existingAdmin.setUsername("admin");
        when(personRepository.findAllSuperAdmin()).thenReturn(List.of(existingAdmin));

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