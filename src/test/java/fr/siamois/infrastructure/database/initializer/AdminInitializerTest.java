package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.initializer.seeder.InstitutionSeeder;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashSet;
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

    @Mock
    private InstitutionSeeder institutionSeeder;


    private AdminInitializer adminInitializer;

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
        existingAdmin.setEmail("admin@example.com");
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



    @Test
    void initializeAdminOrganization_shouldAddSuperAdminAsManager_whenCreatedAdminIsNotManagerAndInitializationExist() throws DatabaseDataInitException {
        Person otherAdmin = new Person();
        otherAdmin.setId(12L);

        Person createdAdmin = new Person();
        createdAdmin.setId(13L);
        createdAdmin.setEmail("admin@example.com");

        Institution existingInstitution = new Institution();
        existingInstitution.setId(14L);
        existingInstitution.setManagers(new HashSet<>());
        existingInstitution.getManagers().add(otherAdmin);

        adminInitializer.setCreatedAdmin(createdAdmin);
        adminInitializer.initializeAdminOrganization();

        // Then: capture argument passed to seed()
        ArgumentCaptor<List<InstitutionSeeder.InstitutionSpec>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(institutionSeeder).seed(captor.capture());

        List<InstitutionSeeder.InstitutionSpec> capturedList = captor.getValue();
        InstitutionSeeder.InstitutionSpec inst = capturedList.get(0);

        // Verify that the InstitutionSpec has the expected properties
        assertEquals("Organisation par défaut", inst.name());
        assertEquals("DEFAULT", inst.description());
        assertEquals("siamois", inst.identifier());
        assertEquals(List.of("admin@example.com"), inst.managerEmails());
        assertEquals("https://thesaurus.mom.fr", inst.baseUri());
        assertEquals("th230", inst.externalId());
    }
}