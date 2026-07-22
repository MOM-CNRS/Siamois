package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AdminInitializerTest {

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private PersonRepository personRepository;

    private AdminInitializer adminInitializer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adminInitializer = new AdminInitializer(passwordEncoder, personRepository);
        adminInitializer.setAdminUsername("admin");
        adminInitializer.setAdminPassword("admin");
        adminInitializer.setAdminEmail("admin@example.com");
    }

    @Test
    void initializeAdmin_shouldCreateAdminWhenNoAdminExists() throws DatabaseDataInitException {
        when(personRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
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
        existingAdmin.setEmail("admin@example.com");
        when(personRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(existingAdmin));

        adminInitializer.initialize();

        assertEquals(existingAdmin, adminInitializer.getCreatedAdmin());
    }

    @Test
    void initializeAdmin_shouldThrowExceptionWhenSaveFails() {
        when(personRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
        when(personRepository.save(any(Person.class))).thenThrow(DataIntegrityViolationException.class);

        assertThrows(DatabaseDataInitException.class, () -> adminInitializer.initializeAdmin());
    }
}
