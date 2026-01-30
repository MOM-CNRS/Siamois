package fr.siamois.utils.context;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemUserLoaderTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @InjectMocks
    private SystemUserLoader systemUserLoader;

    private Person mockSystemPerson;
    private Institution mockDefaultInstitution;

    @BeforeEach
    void setUp() {
        // Initialisation des objets mockés
        mockSystemPerson = new Person();
        mockSystemPerson.setUsername("system");
        mockSystemPerson.setEnabled(true);
        mockSystemPerson.setName("SIAMOIS");
        mockSystemPerson.setLastname("SYSTEM");
        mockSystemPerson.setEmail("system@siamois.fr");
        mockSystemPerson.setPassword("SIAMOIS_UNHASHED");
        mockSystemPerson.setSuperAdmin(false);

        mockDefaultInstitution = new Institution();
        mockDefaultInstitution.setName("Organisation par défaut");
        mockDefaultInstitution.setDescription("DEFAULT");
        mockDefaultInstitution.setIdentifier("siamois");
    }

    @Test
    void loadSystemUser_WhenPersonAndInstitutionExist_ReturnsUserInfo() {
        // --- Préparation des mocks ---
        when(personRepository.findByUsernameIgnoreCase("system"))
                .thenReturn(Optional.of(mockSystemPerson));
        when(institutionRepository.findInstitutionByIdentifier("siamois"))
                .thenReturn(Optional.of(mockDefaultInstitution));

        // --- Exécution ---
        UserInfo result = systemUserLoader.loadSystemUser();

        // --- Vérifications ---
        assertNotNull(result);
        assertEquals("system", result.getUser().getUsername());
        assertEquals("siamois", result.getInstitution().getIdentifier());
        assertEquals("en", result.getLang());

        // Vérifie que les méthodes de sauvegarde ne sont pas appelées
        verify(personRepository, never()).save(any(Person.class));
        verify(institutionRepository, never()).save(any(Institution.class));
    }

    @Test
    void loadSystemUser_WhenPersonDoesNotExist_CreatesAndReturnsUserInfo() {
        // --- Préparation des mocks ---
        when(personRepository.findByUsernameIgnoreCase("system"))
                .thenReturn(Optional.empty());
        when(personRepository.save(any(Person.class)))
                .thenReturn(mockSystemPerson);
        when(institutionRepository.findInstitutionByIdentifier("siamois"))
                .thenReturn(Optional.of(mockDefaultInstitution));

        // --- Exécution ---
        UserInfo result = systemUserLoader.loadSystemUser();

        // --- Vérifications ---
        assertNotNull(result);
        assertEquals("system", result.getUser().getUsername());
        assertEquals("siamois", result.getInstitution().getIdentifier());
        assertEquals("en", result.getLang());

        // Vérifie que la méthode de sauvegarde est appelée pour la personne
        verify(personRepository, times(1)).save(any(Person.class));
        verify(institutionRepository, never()).save(any(Institution.class));
    }

    @Test
    void loadSystemUser_WhenInstitutionDoesNotExist_CreatesAndReturnsUserInfo() {
        // --- Préparation des mocks ---
        when(personRepository.findByUsernameIgnoreCase("system"))
                .thenReturn(Optional.of(mockSystemPerson));
        when(institutionRepository.findInstitutionByIdentifier("siamois"))
                .thenReturn(Optional.empty());
        when(institutionRepository.save(any(Institution.class)))
                .thenReturn(mockDefaultInstitution);

        // --- Exécution ---
        UserInfo result = systemUserLoader.loadSystemUser();

        // --- Vérifications ---
        assertNotNull(result);
        assertEquals("system", result.getUser().getUsername());
        assertEquals("siamois", result.getInstitution().getIdentifier());
        assertEquals("en", result.getLang());

        // Vérifie que la méthode de sauvegarde est appelée pour l'institution
        verify(institutionRepository, times(1)).save(any(Institution.class));
        verify(personRepository, never()).save(any(Person.class));
    }

    @Test
    void loadSystemUser_WhenNeitherPersonNorInstitutionExist_CreatesAndReturnsUserInfo() {
        // --- Préparation des mocks ---
        when(personRepository.findByUsernameIgnoreCase("system"))
                .thenReturn(Optional.empty());
        when(personRepository.save(any(Person.class)))
                .thenReturn(mockSystemPerson);
        when(institutionRepository.findInstitutionByIdentifier("siamois"))
                .thenReturn(Optional.empty());
        when(institutionRepository.save(any(Institution.class)))
                .thenReturn(mockDefaultInstitution);

        // --- Exécution ---
        UserInfo result = systemUserLoader.loadSystemUser();

        // --- Vérifications ---
        assertNotNull(result);
        assertEquals("system", result.getUser().getUsername());
        assertEquals("siamois", result.getInstitution().getIdentifier());
        assertEquals("en", result.getLang());

        // Vérifie que les méthodes de sauvegarde sont appelées pour la personne et l'institution
        verify(personRepository, times(1)).save(any(Person.class));
        verify(institutionRepository, times(1)).save(any(Institution.class));
    }
}
