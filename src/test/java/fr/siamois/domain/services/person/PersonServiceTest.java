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
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PendingInstitutionInviteRepository;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.settings.PersonSettingsRepository;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.email.EmailManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
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
    private ConversionService conversionService;
    @Mock
    private InstitutionMapper institutionMapper;
    @Mock
    private ActionUnitMapper actionUnitMapper;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private PersonMapper personMapper;



    @Mock
    private PendingInstitutionInviteRepository pendingInstitutionInviteRepository;

    private PersonService personService;

    Person person;
    PersonDTO personDto;

    @BeforeEach
    void setUp() {
        person = new Person();
        person.setId(1L);
        person.setPassword("password");
        person.setEmail("mail@localhost.com");
        personDto = new PersonDTO();
        personDto.setId(1L);
        personDto.setEmail("mail@localhost.com");

        emailVerifier = new EmailVerifier(personRepository);

        List<PersonDataVerifier> verifiers = List.of(emailVerifier);
        List<PasswordVerifier> passVerifiers = List.of(passwordVerifier);

        personService = new PersonService(
                personRepository,
                passwordEncoder,
                verifiers,
                passVerifiers,
                personSettingsRepository,
                institutionService,
                langService,
                pendingPersonRepository,
                pendingPersonService,
                conversionService,
                pendingInstitutionInviteRepository,
                institutionMapper,
                actionUnitMapper,
                personMapper,
                conceptMapper
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
        personDto.setId(1L);
        when(conversionService.convert(any(PersonDTO.class), eq(Person.class))).thenReturn(person);
        when(personRepository.findById(personDto.getId())).thenReturn(Optional.of(person)).thenReturn(Optional.of(person));

        // Act
        personService.updatePerson(personDto);

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
        personService.updatePassword(1L, "newPassword");

        // Assert
        verify(personRepository).updatePasswordById(1L, "encodedNewPassword");
    }

    @Test
    void createOrGetSettingsOf() {
        person = new Person();
        person.setId(1L);
        PersonSettings settings = new PersonSettings();
        settings.setPerson(person);
        personDto.setId(1L);

        when(personSettingsRepository.findByPersonId(1L)).thenReturn(Optional.empty());
        when(personSettingsRepository.save(any(PersonSettings.class))).thenReturn(settings);

        PersonSettings result = personService.createOrGetSettingsOf(personDto);

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

        when(conversionService.convert(any(PersonDTO.class), eq(Person.class))).thenReturn(person);

        personService.createPerson(personDto, "password");

        verify(personRepository, times(1)).save(person);
    }

    @Test
    void createPerson_ThrowsInvalidEmailException() {
        personDto.setEmail("invalid-email");
        assertThrows(InvalidEmailException.class, () -> personService.createPerson(personDto, "password"));
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
        List<PasswordVerifier> passVerifiers = List.of();
        personService = new PersonService(
                personRepository,
                passwordEncoder,
                verifiers,
                passVerifiers,
                personSettingsRepository,
                institutionService,
                langService,
                pendingPersonRepository,
                pendingPersonService,
                conversionService,
                pendingInstitutionInviteRepository,
                institutionMapper,
                actionUnitMapper,
                personMapper,
                conceptMapper
        );

        // Act
        Optional<PasswordVerifier> verifier = personService.findPasswordVerifier();

        // Assert
        assertTrue(verifier.isEmpty());
    }

    @Test
    void findAllAuthorsOfSpatialUnitByInstitution_Success() {

        PersonDTO p = new PersonDTO();
        Person pjpa = new Person();
        InstitutionDTO i = new InstitutionDTO();
        i.setId(1L);

        when(personRepository.findAllAuthorsOfSpatialUnitByInstitution(1L)).thenReturn(
                List.of(pjpa));
        when(conversionService.convert(any(Person.class), eq(PersonDTO.class))).thenReturn(p);

        // Act
        List<PersonDTO> res = personService.findAllAuthorsOfSpatialUnitByInstitution(i);

        // Assert
        assertEquals(1, res.size());
        assertEquals(p, res.get(0));


    }

    @Test
    void findAllAuthorsOfActionUnitByInstitution_Success() {


        Person pjpa = new Person();
        InstitutionDTO i = new InstitutionDTO();
        i.setId(1L);

        when(personRepository.findAllAuthorsOfActionUnitByInstitution(1L)).thenReturn(
                List.of(pjpa));

        // Act
        List<Person> res = personService.findAllAuthorsOfActionUnitByInstitution(i);

        // Assert
        assertEquals(1, res.size());
        assertEquals(pjpa, res.get(0));
    }

    @Test
    void findClosestByUsernameOrEmail_ShouldReturnEmptyList_WhenInputIsNullOrBlank() {
        assertTrue(personService.findClosestByUsernameOrEmail(null).isEmpty());
        assertTrue(personService.findClosestByUsernameOrEmail("").isEmpty());
        assertTrue(personService.findClosestByUsernameOrEmail("   ").isEmpty());
    }

    @Test
    void findClosestByUsernameOrEmail_ShouldReturnUnionOfResults() {
        Person p1 = new Person();
        p1.setId(1L);
        p1.setEmail("bob");
        Person p2 = new Person();
        p2.setId(2L);
        p2.setUsername("bob");

        when(personRepository.findClosestByEmailLimit10("bob")).thenReturn(Set.of(p1));
        when(personRepository.findClosestByUsernameLimit10("bob")).thenReturn(Set.of(p2));

        var res = personService.findClosestByUsernameOrEmail("bob");
        assertEquals(2, res.size());
    }

    @Test
    void findClosestByUsernameOrEmail_ShouldRemoveDuplicates() {
        Person p1 = new Person();
        p1.setId(1L);

        when(personRepository.findClosestByEmailLimit10("bob")).thenReturn(Set.of(p1));
        when(personRepository.findClosestByUsernameLimit10("bob")).thenReturn(Set.of(p1));

        var res = personService.findClosestByUsernameOrEmail("bob");
        assertEquals(1, res.size());

    }

    // Pour createAndDeletePendingRelations, on teste via createPerson (chemins principaux)
    @Test
    void createAndDeletePendingRelations_ShouldAddManagerAndActionManagerAndAttributions() throws Exception {
        // Arrange
        Person newPerson = new Person();
        newPerson.setId(42L);
        PersonDTO newPersonDto = new PersonDTO();
        newPersonDto.setId(42L);
        newPersonDto.setEmail("mail@localhost.com");
        PendingPerson pendingPerson = new PendingPerson();
        pendingPerson.setEmail("mail@localhost.com");
        newPerson.setPassword("password");
        newPersonDto.setEmail("mail@localhost.com");

        PersonDTO newPersonRequest = new PersonDTO();
        newPersonRequest.setId(42L);
        newPersonRequest.setEmail("mail@localhost.com");

        Institution institution = new Institution();
        institution.setId(1L);
        InstitutionDTO institutionDTO = new InstitutionDTO();
        institutionDTO.setId(1L);

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
        when(conversionService.convert(any(PersonDTO.class),eq(Person.class))).thenReturn(newPerson);
        when(institutionMapper.convert(any(Institution.class))).thenReturn(institutionDTO);
        when(personMapper.convert(any(Person.class))).thenReturn(newPersonDto);
        ConceptDTO c  = new ConceptDTO(); c.setId(2L);
        when(conceptMapper.convert(any(Concept.class))).thenReturn(c);
        // Act
;
        personService.createPerson(newPersonRequest,"password");

        // Assert

        verify(institutionService).addToManagers(institutionDTO, newPersonDto);
        verify(institutionService).addPersonToActionManager(institutionDTO, newPersonDto);
        verify(institutionService).addPersonToActionUnit(null, newPersonDto, c);
        verify(pendingPersonService).delete(attribution);
        verify(pendingPersonService).delete(invite);
        verify(pendingPersonRepository).delete(pendingPerson);
    }

    @Test
    void createAndDeletePendingRelations_ShouldNotAddManagerOrActionManager_WhenFlagsFalse() throws Exception {
        // Arrange
        Person newPerson = new Person();
        newPerson.setId(42L);
        PersonDTO newPersonDto = new PersonDTO();
        newPersonDto.setId(42L);
        PendingPerson pendingPerson = new PendingPerson();
        pendingPerson.setEmail("mail@localhost.com");

        Institution institution = new Institution();
        institution.setId(1L);

        PendingInstitutionInvite invite = mock(PendingInstitutionInvite.class);
        when(invite.getInstitution()).thenReturn(institution);
        when(invite.isManager()).thenReturn(false);
        when(invite.isActionManager()).thenReturn(false);

        Set<PendingInstitutionInvite> invites = Set.of(invite);
        when(pendingInstitutionInviteRepository.findAllByPendingPerson(any())).thenReturn(invites);
        when(pendingPersonService.findActionAttributionsByPendingInvite(invite)).thenReturn(Set.of());

        when(pendingPersonService.createOrGetPendingPerson(any())).thenReturn(pendingPerson);
        when(personRepository.save(any(Person.class))).thenReturn(newPerson);

        when(conversionService.convert(any(PersonDTO.class),eq(Person.class))).thenReturn(newPerson);

        // Act
        person.setPassword("password");
        person.setEmail("mail@localhost.com");
        person.setId(-1L);
        PersonDTO created = personService.createPerson(personDto, "password");

        // Assert
        verify(institutionService, never()).addToManagers(any(), any());
        verify(institutionService, never()).addPersonToActionManager(any(), any());
        verify(institutionService, never()).addPersonToActionUnit(any(), any(), any());
        verify(pendingPersonService, never()).delete(any(PendingActionUnitAttribution.class));
        verify(pendingPersonService).delete(invite);
        verify(pendingPersonRepository).delete(pendingPerson);
    }

    @Test
    void createAndDeletePendingRelations_ShouldHandleNoInvites() throws Exception {
        // Arrange
        Person newPerson = new Person();
        newPerson.setId(42L);
        PendingPerson pendingPerson = new PendingPerson();
        pendingPerson.setEmail("mail@localhost.com");
        PersonDTO newPersonDto = new PersonDTO();
        newPersonDto.setId(42L);
        newPersonDto.setEmail("mail@localhost.com");

        when(pendingInstitutionInviteRepository.findAllByPendingPerson(any())).thenReturn(Set.of());
        when(pendingPersonService.createOrGetPendingPerson(any())).thenReturn(pendingPerson);
        when(personRepository.save(any(Person.class))).thenReturn(newPerson);
        when(conversionService.convert(any(PersonDTO.class),eq(Person.class))).thenReturn(person);
        // Act
        person.setPassword("password");
        person.setEmail("mail@localhost.com");
        person.setId(-1L);
        PersonDTO created = personService.createPerson(newPersonDto, "password");

        // Assert
        verify(institutionService, never()).addToManagers(any(), any());
        verify(institutionService, never()).addPersonToActionManager(any(), any());
        verify(institutionService, never()).addPersonToActionUnit(any(), any(), any());
        verify(pendingPersonService, never()).delete(any(PendingActionUnitAttribution.class));
        verify(pendingPersonService, never()).delete(any(PendingInstitutionInvite.class));
        verify(pendingPersonRepository).delete(pendingPerson);
    }

}