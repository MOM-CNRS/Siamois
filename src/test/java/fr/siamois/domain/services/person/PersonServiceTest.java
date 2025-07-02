package fr.siamois.domain.services.person;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.pending.PendingActionUnitAttribution;
import fr.siamois.domain.models.auth.pending.PendingInstitutionInvite;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.PersonSettings;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.verifier.EmailVerifier;
import fr.siamois.domain.services.person.verifier.PasswordVerifier;
import fr.siamois.domain.services.person.verifier.PersonDataVerifier;
import fr.siamois.infrastructure.database.repositories.person.PendingInstitutionInviteRepository;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.settings.PersonSettingsRepository;
import fr.siamois.ui.email.EmailManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

    @Mock
    private PersonRepository personRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    private EmailVerifier emailVerifier;
    private final PasswordVerifier passwordVerifier = new PasswordVerifier();
    @Mock
    private PersonSettingsRepository personSettingsRepository;
    @Mock
    private InstitutionService institutionService;
    @Mock
    private LangService langService;
    @Mock
    private EmailManager emailManager;
    @Mock
    private PendingPersonRepository pendingPersonRepository;
    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private PendingPersonService pendingPersonService;

    @Mock
    private PendingInstitutionInviteRepository pendingInstitutionInviteRepository;

    private PersonService personService;

    Person person;

    @BeforeEach
    void setUp() {
        person = new Person();
        person.setId(1L);
        person.setPassword("password");
        person.setEmail("mail@localhost.com");

        emailVerifier = new EmailVerifier(personRepository);

        List<PersonDataVerifier> verifiers = List.of(passwordVerifier, emailVerifier);

        personService = new PersonService(
                personRepository,
                passwordEncoder,
                verifiers,
                personSettingsRepository,
                institutionService,
                langService,
                pendingPersonRepository,
                pendingPersonService,
                pendingInstitutionInviteRepository
        );
    }

    @Test
    void findAllByNameLastnameContaining_Success() {
        when(personRepository.findAllByNameOrLastname("bob")).thenReturn(List.of(person));

        // Act
        List<Person> actualResult = personService.findAllByNameLastnameContaining("bob");

        // Assert
        assertEquals(List.of(person), actualResult);
    }

    @Test
    void updatePerson_Success() throws UserAlreadyExistException, InvalidNameException, InvalidPasswordException, InvalidUsernameException, InvalidEmailException {
        // Arrange
        when(personRepository.save(person)).thenReturn(person);

        // Act
        personService.updatePerson(person);

        // Assert
        verify(personRepository, times(1)).save(person);
    }

    @Test
    void passwordMatch_Success() {
        // Arrange
        when(passwordEncoder.matches("plainPassword", "encodedPassword")).thenReturn(true);
        person.setPassword("encodedPassword");

        // Act
        boolean result = personService.passwordMatch(person, "plainPassword");

        // Assert
        assertTrue(result);
    }

    @Test
    void updatePassword_Success() throws InvalidPasswordException {
        // Arrange

        when(passwordEncoder.encode("newPassword")).thenReturn("encodedNewPassword");

        // Act
        personService.updatePassword(person, "newPassword");

        // Assert
        assertEquals("encodedNewPassword", person.getPassword());
    }

    @Test
    void createOrGetSettingsOf() {
        person = new Person();
        person.setId(1L);
        PersonSettings settings = new PersonSettings();
        settings.setPerson(person);

        when(personSettingsRepository.findByPerson(person)).thenReturn(Optional.empty());
        when(personSettingsRepository.save(any(PersonSettings.class))).thenReturn(settings);

        PersonSettings result = personService.createOrGetSettingsOf(person);

        assertNotNull(result);
        assertEquals(person, result.getPerson());
        verify(personSettingsRepository).save(any(PersonSettings.class));
    }

    @Test
    void updatePersonSettings() {
        PersonSettings settings = new PersonSettings();
        settings.setPerson(person);
        when(personSettingsRepository.save(settings)).thenReturn(settings);

        PersonSettings result = personService.updatePersonSettings(settings);

        assertNotNull(result);
        assertEquals(settings, result);
        verify(personSettingsRepository).save(settings);
    }

    @Test
    void createPerson_Success() throws Exception {
        when(personRepository.save(any(Person.class))).thenReturn(person);

        Person result = personService.createPerson(person);

        assertNotNull(result);
        verify(personRepository, times(1)).save(person);
    }

    @Test
    void createPerson_ThrowsInvalidEmailException() {
        person.setEmail("invalid-email");
        assertThrows(InvalidEmailException.class, () -> personService.createPerson(person));
    }

    @Test
    void findById_Success() {
        when(personRepository.findById(1L)).thenReturn(Optional.of(person));

        Person result = personService.findById(1L);

        assertNotNull(result);
        assertEquals(person, result);
        verify(personRepository, times(1)).findById(1L);
    }

    @Test
    void findById_NotFound() {
        when(personRepository.findById(1L)).thenReturn(Optional.empty());

        Person result = personService.findById(1L);

        assertNull(result);
        verify(personRepository, times(1)).findById(1L);
    }

    @Test
    void findPasswordVerifier_Success() {
        Optional<PasswordVerifier> verifier = personService.findPasswordVerifier();

        assertTrue(verifier.isPresent());
        assertEquals(passwordVerifier, verifier.get());
    }

    @Test
    void findByEmail_Success() {
        when(personRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(person));

        Optional<Person> result = personService.findByEmail("test@example.com");

        assertTrue(result.isPresent());
        assertEquals(person, result.get());
        verify(personRepository, times(1)).findByEmailIgnoreCase("test@example.com");
    }

    @Test
    void findPasswordVerifier_ShouldReturnVerifier_WhenPresent() {
        // Act
        Optional<PasswordVerifier> verifier = personService.findPasswordVerifier();

        // Assert
        assertTrue(verifier.isPresent());
        assertEquals(PasswordVerifier.class, verifier.get().getClass());
    }

    @Test
    void findPasswordVerifier_ShouldReturnEmpty_WhenNotPresent() {
        // Arrange
        List<PersonDataVerifier> verifiers = List.of(); // Liste vide
        personService = new PersonService(
                personRepository,
                passwordEncoder,
                verifiers,
                personSettingsRepository,
                institutionService,
                langService,
                pendingPersonRepository,
                pendingPersonService,
                pendingInstitutionInviteRepository
        );

        // Act
        Optional<PasswordVerifier> verifier = personService.findPasswordVerifier();

        // Assert
        assertTrue(verifier.isEmpty());
    }

    @Test
    void findAllAuthorsOfSpatialUnitByInstitution_Success() {

        Person p = new Person();
        Institution i = new Institution(); i.setId(1L);

        when(personRepository.findAllAuthorsOfSpatialUnitByInstitution(1L)).thenReturn(
                List.of(p));

        // Act
        List<Person> res = personService.findAllAuthorsOfSpatialUnitByInstitution(i);

        // Assert
        assertEquals(1, res.size());
        assertEquals(p, res.get(0));


    }

    @Test
    void findAllAuthorsOfActionUnitByInstitution_Success() {

        Person p = new Person();
        Institution i = new Institution(); i.setId(1L);

        when(personRepository.findAllAuthorsOfActionUnitByInstitution(1L)).thenReturn(
                List.of(p));

        // Act
        List<Person> res = personService.findAllAuthorsOfActionUnitByInstitution(i);

        // Assert
        assertEquals(1, res.size());
        assertEquals(p, res.get(0));
    }

    @Test
    void findClosestByUsernameOrEmail_ShouldReturnEmptyList_WhenInputIsNullOrBlank() {
        assertTrue(personService.findClosestByUsernameOrEmail(null).isEmpty());
        assertTrue(personService.findClosestByUsernameOrEmail("").isEmpty());
        assertTrue(personService.findClosestByUsernameOrEmail("   ").isEmpty());
    }

    @Test
    void findClosestByUsernameOrEmail_ShouldReturnUnionOfResults() {
        Person p1 = new Person(); p1.setId(1L);
        Person p2 = new Person(); p2.setId(2L);

        when(personRepository.findClosestByEmailLimit10("bob")).thenReturn(Set.of(p1));
        when(personRepository.findClosestByUsernameLimit10("bob")).thenReturn(Set.of(p2));

        var res = personService.findClosestByUsernameOrEmail("bob");
        assertEquals(2, res.size());
        assertTrue(res.contains(p1));
        assertTrue(res.contains(p2));
    }

    @Test
    void findClosestByUsernameOrEmail_ShouldRemoveDuplicates() {
        Person p1 = new Person(); p1.setId(1L);

        when(personRepository.findClosestByEmailLimit10("bob")).thenReturn(Set.of(p1));
        when(personRepository.findClosestByUsernameLimit10("bob")).thenReturn(Set.of(p1));

        var res = personService.findClosestByUsernameOrEmail("bob");
        assertEquals(1, res.size());
        assertTrue(res.contains(p1));
    }

    // Pour createAndDeletePendingRelations, on teste via createPerson (chemins principaux)
    @Test
    void createAndDeletePendingRelations_ShouldAddManagerAndActionManagerAndAttributions() throws Exception {
        // Arrange
        Person newPerson = new Person(); newPerson.setId(42L);
        PendingPerson pendingPerson = new PendingPerson();
        pendingPerson.setEmail("mail@localhost.com");

        Institution institution = new Institution(); institution.setId(1L);

        PendingInstitutionInvite invite = mock(PendingInstitutionInvite.class);
        when(invite.getInstitution()).thenReturn(institution);
        when(invite.isManager()).thenReturn(true);
        when(invite.isActionManager()).thenReturn(true);

        PendingActionUnitAttribution attribution = mock(PendingActionUnitAttribution.class);
        when(attribution.getActionUnit()).thenReturn(null);
        when(attribution.getRole()).thenReturn(new Concept());

        Set<PendingInstitutionInvite> invites = Set.of(invite);
        Set<PendingActionUnitAttribution> attributions = Set.of(attribution);

        when(pendingInstitutionInviteRepository.findAllByPendingPerson(any())).thenReturn(invites);
        when(pendingPersonService.findActionAttributionsByPendingInvite(invite)).thenReturn(attributions);

        // On mock la création du PendingPerson
        when(pendingPersonService.createOrGetPendingPerson(any())).thenReturn(pendingPerson);

        // On mock le save du person
        when(personRepository.save(any(Person.class))).thenReturn(newPerson);

        // Act
        person.setPassword("password");
        person.setEmail("mail@localhost.com");
        person.setId(-1L);
        Person created = personService.createPerson(person);

        // Assert
        verify(institutionService).addToManagers(institution, newPerson);
        verify(institutionService).addPersonToActionManager(institution, newPerson);
        verify(institutionService).addPersonToActionUnit(null, newPerson, new Concept());
        verify(pendingPersonService).delete(attribution);
        verify(pendingPersonService).delete(invite);
        verify(pendingPersonRepository).delete(pendingPerson);
        assertNotNull(created);
    }

    @Test
    void createAndDeletePendingRelations_ShouldNotAddManagerOrActionManager_WhenFlagsFalse() throws Exception {
        // Arrange
        Person newPerson = new Person(); newPerson.setId(42L);
        PendingPerson pendingPerson = new PendingPerson();
        pendingPerson.setEmail("mail@localhost.com");

        Institution institution = new Institution(); institution.setId(1L);

        PendingInstitutionInvite invite = mock(PendingInstitutionInvite.class);
        when(invite.getInstitution()).thenReturn(institution);
        when(invite.isManager()).thenReturn(false);
        when(invite.isActionManager()).thenReturn(false);

        Set<PendingInstitutionInvite> invites = Set.of(invite);
        when(pendingInstitutionInviteRepository.findAllByPendingPerson(any())).thenReturn(invites);
        when(pendingPersonService.findActionAttributionsByPendingInvite(invite)).thenReturn(Set.of());

        when(pendingPersonService.createOrGetPendingPerson(any())).thenReturn(pendingPerson);
        when(personRepository.save(any(Person.class))).thenReturn(newPerson);

        // Act
        person.setPassword("password");
        person.setEmail("mail@localhost.com");
        person.setId(-1L);
        Person created = personService.createPerson(person);

        // Assert
        verify(institutionService, never()).addToManagers(any(), any());
        verify(institutionService, never()).addPersonToActionManager(any(), any());
        verify(institutionService, never()).addPersonToActionUnit(any(), any(), any());
        verify(pendingPersonService, never()).delete(any(PendingActionUnitAttribution.class));
        verify(pendingPersonService).delete(invite);
        verify(pendingPersonRepository).delete(pendingPerson);
        assertNotNull(created);
    }

    @Test
    void createAndDeletePendingRelations_ShouldHandleNoInvites() throws Exception {
        // Arrange
        Person newPerson = new Person(); newPerson.setId(42L);
        PendingPerson pendingPerson = new PendingPerson();
        pendingPerson.setEmail("mail@localhost.com");

        when(pendingInstitutionInviteRepository.findAllByPendingPerson(any())).thenReturn(Set.of());
        when(pendingPersonService.createOrGetPendingPerson(any())).thenReturn(pendingPerson);
        when(personRepository.save(any(Person.class))).thenReturn(newPerson);

        // Act
        person.setPassword("password");
        person.setEmail("mail@localhost.com");
        person.setId(-1L);
        Person created = personService.createPerson(person);

        // Assert
        verify(institutionService, never()).addToManagers(any(), any());
        verify(institutionService, never()).addPersonToActionManager(any(), any());
        verify(institutionService, never()).addPersonToActionUnit(any(), any(), any());
        verify(pendingPersonService, never()).delete(any(PendingActionUnitAttribution.class));
        verify(pendingPersonService, never()).delete(any(PendingInstitutionInvite.class));
        verify(pendingPersonRepository).delete(pendingPerson);
        assertNotNull(created);
    }

}