package fr.siamois.domain.services.person;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.pending.PendingActionUnitAttribution;
import fr.siamois.domain.models.auth.pending.PendingInstitutionInvite;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.PersonSettings;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.verifier.EmailVerifier;
import fr.siamois.domain.services.person.verifier.PasswordVerifier;
import fr.siamois.domain.services.person.verifier.PersonDataVerifier;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PendingInstitutionInviteRepository;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.settings.PersonSettingsRepository;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.email.EmailManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.convert.ConversionService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.isNull;
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
                personMapper
        );
    }

    @Test
    void findAllByNameLastnameContaining_Success() {
        when(personRepository.findAllByNameOrLastname("bob", 100)).thenReturn(List.of(person));

        // Act
        List<PersonDTO> actualResult = personService.findAllByNameLastnameContaining("bob");

        // Assert
        assertEquals(1, actualResult.size());
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
        doReturn(Optional.of(person)).when(personRepository).findById(any(Long.class));
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
                personMapper
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
        // Act

        personService.createPerson(newPersonRequest,"password");

        // Assert

        verify(institutionService).addToManagers(institutionDTO, newPersonDto);
        verify(institutionService).addPersonToActionManager(institutionDTO, newPersonDto);
        verify(institutionService).addPersonAsMemberOfActionUnit(null, newPersonDto);
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
        personService.createPerson(personDto, "password");

        // Assert
        verify(institutionService, never()).addToManagers(any(), any());
        verify(institutionService, never()).addPersonToActionManager(any(), any());
        verify(institutionService, never()).addPersonAsMemberOfActionUnit(any(), any());
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
        personService.createPerson(personDto, "password");

        // Assert
        verify(institutionService, never()).addToManagers(any(), any());
        verify(institutionService, never()).addPersonToActionManager(any(), any());
        verify(institutionService, never()).addPersonAsMemberOfActionUnit(any(), any());
        verify(pendingPersonService, never()).delete(any(PendingActionUnitAttribution.class));
        verify(pendingPersonService, never()).delete(any(PendingInstitutionInvite.class));
        verify(pendingPersonRepository).delete(pendingPerson);
    }

    @Test
    void findAllInInstitution_nullSearch_passesNullFilter() {
        when(personRepository.findAllInInstitution(10L, null)).thenReturn(List.of(person));
        when(personMapper.convert(person)).thenReturn(personDto);

        List<PersonDTO> result = personService.findAllInInstitution(10L, null);

        assertEquals(1, result.size());
        verify(personRepository).findAllInInstitution(10L, null);
    }

    @Test
    void findAllInInstitution_blankSearch_passesNullFilter() {
        when(personRepository.findAllInInstitution(10L, null)).thenReturn(List.of());

        personService.findAllInInstitution(10L, "   ");

        verify(personRepository).findAllInInstitution(10L, null);
    }

    @Test
    void findAllInInstitution_withSearch_trimsFilter() {
        when(personRepository.findAllInInstitution(10L, "martin")).thenReturn(List.of(person));
        when(personMapper.convert(person)).thenReturn(personDto);

        List<PersonDTO> result = personService.findAllInInstitution(10L, "  martin  ");

        assertEquals(1, result.size());
        verify(personRepository).findAllInInstitution(10L, "martin");
    }

    @Test
    void createOrGetSettingsOf_returnsExisting_whenPresent() {
        PersonSettings existing = new PersonSettings();
        existing.setPerson(person);
        when(personSettingsRepository.findByPersonId(1L)).thenReturn(Optional.of(existing));

        PersonSettings result = personService.createOrGetSettingsOf(personDto);

        assertSame(existing, result);
        verify(personSettingsRepository, never()).save(any());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void createOrGetSettingsOf_createsNew_whenAbsent() {
        InstitutionDTO institutionDto = new InstitutionDTO();
        institutionDto.setId(5L);
        Institution institution = new Institution();
        institution.setId(5L);

        personDto.setId(1L);
        when(personSettingsRepository.findByPersonId(1L)).thenReturn(Optional.empty());
        doReturn(Optional.of(person)).when(personRepository).findById(any(Long.class));
        when(institutionService.findInstitutionsOfPerson(personDto)).thenReturn(Set.of(institutionDto));
        when(langService.getDefaultLang()).thenReturn("fr");
        when(conversionService.convert(any(InstitutionDTO.class), eq(Institution.class))).thenReturn(institution);
        when(personSettingsRepository.save(any(PersonSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<PersonSettings> savedCaptor = ArgumentCaptor.forClass(PersonSettings.class);

        PersonSettings result = personService.createOrGetSettingsOf(personDto);

        verify(personSettingsRepository).save(savedCaptor.capture());
        assertNotNull(result);
        assertEquals(person, savedCaptor.getValue().getPerson());
        assertEquals(institution, result.getDefaultInstitution());
        assertEquals("fr", result.getLangCode());
        assertEquals(person, result.getPerson());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void createOrGetSettingsOf_createsPersonWhenMissing() {
        when(personSettingsRepository.findByPersonId(anyLong())).thenReturn(Optional.empty());
        when(personRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(personMapper.invertConvert(any(PersonDTO.class))).thenReturn(person);
        when(personRepository.save(person)).thenReturn(person);
        when(institutionService.findInstitutionsOfPerson(any(PersonDTO.class))).thenReturn(Set.of());
        when(langService.getDefaultLang()).thenReturn("en");
        when(conversionService.convert(isNull(), eq(Institution.class))).thenReturn(null);
        when(personSettingsRepository.save(any(PersonSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<PersonSettings> savedCaptor = ArgumentCaptor.forClass(PersonSettings.class);

        PersonSettings result = personService.createOrGetSettingsOf(personDto);

        verify(personSettingsRepository).save(savedCaptor.capture());
        assertNotNull(result);
        verify(personRepository).save(person);
        assertEquals(person, savedCaptor.getValue().getPerson());
        assertEquals("en", result.getLangCode());
        assertNull(result.getDefaultInstitution());
    }

    @Test
    void updatePerson_withPassword_savesConvertedPerson() throws Exception {
        when(conversionService.convert(personDto, Person.class)).thenReturn(person);
        when(personRepository.save(person)).thenReturn(person);

        personService.updatePerson(personDto, "newPassword");

        verify(personRepository).save(person);
    }

    @Test
    void updatePassword_throwsWhenVerifierMissing() {
        PersonService serviceWithoutVerifier = new PersonService(
                personRepository,
                passwordEncoder,
                List.of(emailVerifier),
                List.of(),
                personSettingsRepository,
                institutionService,
                langService,
                pendingPersonRepository,
                pendingPersonService,
                conversionService,
                pendingInstitutionInviteRepository,
                institutionMapper,
                actionUnitMapper,
                personMapper
        );

        assertThrows(IllegalStateException.class, () -> serviceWithoutVerifier.updatePassword(1L, "password"));
    }

}