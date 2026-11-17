package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.dataimport.ImportSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonSeederTest {

    @Mock
    PersonRepository personRepository;

    @Mock
    PersonProfileAssignmentService personProfileAssignmentService;

    @InjectMocks
    PersonSeeder seeder;

    @Test
    void seed_AlreadyExists() {
        Person p = new Person();
        List<PersonSeeder.PersonSpec> toInsert = List.of(
                new PersonSeeder.PersonSpec("user@siamois.fr", "name", "lastname", "username")
        );
        when(personRepository.findByNameIgnoreCaseAndLastnameIgnoreCase("name", "lastname")).thenReturn(Optional.of(p));
        Map<String, Person> res = seeder.seed(toInsert);
        assertNotNull(res.get("user@siamois.fr"));
        verify(personRepository, never()).save(any(Person.class));
    }

    @Test
    void seed_DoesNotExists() {
        List<PersonSeeder.PersonSpec> toInsert = List.of(
                new PersonSeeder.PersonSpec("user@siamois.fr", "name", "lastname", "username")
        );
        when(personRepository.findByNameIgnoreCaseAndLastnameIgnoreCase("name", "lastname")).thenReturn(Optional.empty());
        when(personRepository.save(any(Person.class))).thenAnswer(returnsFirstArg());
        Map<String, Person> res = seeder.seed(toInsert);
        assertNotNull(res.get("user@siamois.fr"));
        verify(personRepository, times(1)).save(any(Person.class));
    }

    @Test
    void findPersonOrThrow_shouldReturnPerson_whenFound() {
        // given
        String email = "test@example.com";
        Person expectedPerson = new Person();
        expectedPerson.setEmail(email);

        when(personRepository.findByEmailIgnoreCase(email))
                .thenReturn(Optional.of(expectedPerson));

        // when
        Person result = seeder.findPersonOrThrow(email);

        // then
        assertNotNull(result);
        assertEquals(expectedPerson, result);
        verify(personRepository).findByEmailIgnoreCase(email);
    }

    @Test
    void findPersonOrThrow_shouldThrowException_whenNotFound() {
        // given
        String email = "missing@example.com";
        when(personRepository.findByEmailIgnoreCase(email))
                .thenReturn(Optional.empty());

        // when + then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> seeder.findPersonOrThrow(email)
        );

        assertEquals("Person missing@example.com introuvable", exception.getMessage());
        verify(personRepository).findByEmailIgnoreCase(email);
    }

    @Test
    void findOrCreatePerson_withInstitution_assignsMemberProfile() {
        Institution inst = new Institution();
        inst.setId(10L);

        Person p = new Person();
        p.setId(42L);
        p.setName("Pascal");
        p.setLastname("Gibut");

        when(personRepository.findByNameIgnoreCaseAndLastnameIgnoreCase("Pascal", "Gibut"))
                .thenReturn(Optional.of(p));

        Person result = seeder.findOrCreatePerson("Pascal Gibut", inst);

        assertNotNull(result);
        assertEquals(p, result);
        verify(personProfileAssignmentService).assignOrganizationMemberProfile(inst, p);
    }

    @Test
    void findOrCreatePerson_withEmail_findsExistingByEmail() {
        Institution inst = new Institution();
        inst.setId(10L);

        Person p = new Person();
        p.setId(42L);
        p.setEmail("pascal.gibut@siamois.fr");

        when(personRepository.findByEmailIgnoreCase("pascal.gibut@siamois.fr"))
                .thenReturn(Optional.of(p));

        Person result = seeder.findOrCreatePerson("pascal.gibut@siamois.fr", inst);

        assertNotNull(result);
        assertEquals(p, result);
        verify(personProfileAssignmentService).assignOrganizationMemberProfile(inst, p);
    }

    @Test
    void findOrCreatePerson_withEmail_createsWhenNotFound() {
        Institution inst = new Institution();
        inst.setId(10L);

        when(personRepository.findByEmailIgnoreCase("marie.curie@siamois.fr"))
                .thenReturn(Optional.empty());
        when(personRepository.findByNameIgnoreCaseAndLastnameIgnoreCase("Marie", "Curie"))
                .thenReturn(Optional.empty());
        when(personRepository.save(any(Person.class))).thenAnswer(invocation -> {
            Person saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        Person result = seeder.findOrCreatePerson("marie.curie@siamois.fr", inst);

        assertNotNull(result);
        assertEquals("Marie", result.getName());
        assertEquals("Curie", result.getLastname());
        assertEquals("marie.curie@siamois.fr", result.getEmail());
        assertEquals(100L, result.getId());
        verify(personRepository).save(any(Person.class));
        verify(personProfileAssignmentService).assignOrganizationMemberProfile(inst, result);
    }

    @Test
    void resolveCached_withInstitution_assignsProfileOnceAndCaches() {
        Institution inst = new Institution();
        inst.setId(10L);

        Person p = new Person();
        p.setId(42L);
        p.setName("Pascal");
        p.setLastname("Gibut");

        when(personRepository.findByNameIgnoreCaseAndLastnameIgnoreCase("Pascal", "Gibut"))
                .thenReturn(Optional.of(p));

        Map<String, Person> cache = new HashMap<>();

        // First call: resolves person and assigns profile
        Person first = seeder.resolveCached(cache, "Pascal Gibut", inst);
        assertSame(p, first);
        verify(personProfileAssignmentService, times(1)).assignOrganizationMemberProfile(inst, p);

        // Second call: served from cache, does not call assignment service again
        Person second = seeder.resolveCached(cache, "Pascal Gibut", inst);
        assertSame(p, second);
        verify(personProfileAssignmentService, times(1)).assignOrganizationMemberProfile(inst, p);
    }

    @Test
    void ensureMemberOfInstitution_systemPerson_doesNotAssign() {
        Institution inst = new Institution();
        inst.setId(10L);

        Person systemPerson = new Person();
        systemPerson.setId(99L);
        systemPerson.setName("siamois");
        systemPerson.setLastname("system");
        systemPerson.setUsername(ImportSchema.SIAMOIS_SYSTEM);

        seeder.ensureMemberOfInstitution(systemPerson, inst);

        verify(personProfileAssignmentService, never()).assignOrganizationMemberProfile(any(Institution.class), any());
    }

    @Test
    void prefetchByNameLastName_prefetchesBothNamesAndEmails() {
        Person pName = new Person();
        pName.setName("Pascal");
        pName.setLastname("Gibut");

        Person pEmail = new Person();
        pEmail.setEmail("author@siamois.fr");

        when(personRepository.findAllByNameIgnoreCaseIn(Set.of("Pascal")))
                .thenReturn(List.of(pName));
        when(personRepository.findByEmailIgnoreCase("author@siamois.fr"))
                .thenReturn(Optional.of(pEmail));

        Map<String, Person> cache = seeder.prefetchByNameLastName(List.of("Pascal Gibut", "author@siamois.fr"));

        assertThat(cache)
                .containsKey("pascal|gibut")
                .containsKey("author@siamois.fr");
    }

    @Test
    void findPersonOrReturnNull_returnsNull_whenNotFound() {
        when(personRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertNull(seeder.findPersonOrReturnNull("missing@example.com"));
    }

    @Test
    void findOrCreatePerson_nullInstitution_trimsInputAndDoesNotAssignProfile() {
        Person p = person(42L, "Pascal", "Gibut");
        when(personRepository.findByNameIgnoreCaseAndLastnameIgnoreCase("Pascal", "Gibut"))
                .thenReturn(Optional.of(p));

        Person result = seeder.findOrCreatePerson("  Pascal Gibut  ", null);

        assertSame(p, result);
        verifyNoInteractions(personProfileAssignmentService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "Pascal", " Gibut"})
    void findOrCreatePerson_invalidName_throws(String nameLastName) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> seeder.findOrCreatePerson(nameLastName, null)
        );

        assertThat(exception.getMessage()).startsWith("Nom/Prénom incorrect: ");
        verify(personRepository, never()).save(any(Person.class));
    }

    @Test
    void findOrCreatePerson_withCompoundFirstname_createsPersonWithJoinedUsername() {
        when(personRepository.save(any(Person.class))).thenAnswer(returnsFirstArg());

        Person result = seeder.findOrCreatePerson("Jean  Pierre Dupont", null);

        assertThat(result)
                .extracting(Person::getName, Person::getLastname, Person::getUsername, Person::getEmail, Person::getPassword)
                .containsExactly("Jean  Pierre", "Dupont", "JeanPierre.Dupont", null, "mysuperstrongpassword");
    }

    @Test
    void findOrCreatePerson_withEmailWithoutDot_usesLocalPartAsNames() {
        when(personRepository.findByEmailIgnoreCase("jdoe@siamois.fr")).thenReturn(Optional.empty());
        when(personRepository.save(any(Person.class))).thenAnswer(returnsFirstArg());

        Person result = seeder.findOrCreatePerson("jdoe@siamois.fr", null);

        assertThat(result)
                .extracting(Person::getName, Person::getLastname, Person::getUsername, Person::getEmail)
                .containsExactly("jdoe", "jdoe", "jdoe", "jdoe@siamois.fr");
    }

    @ParameterizedTest
    @ValueSource(strings = {".jdoe@siamois.fr", "jdoe.@siamois.fr"})
    void findOrCreatePerson_withEmailDotAtBoundary_doesNotSplitLocalPart(String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        when(personRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(personRepository.save(any(Person.class))).thenAnswer(returnsFirstArg());

        Person result = seeder.findOrCreatePerson(email, null);

        assertThat(result)
                .extracting(Person::getName, Person::getLastname)
                .containsExactly(localPart, localPart);
    }

    @Test
    void findOrCreatePerson_existingByNameAndLastname_isNotSavedAgain() {
        Person p = person(42L, "Marie", "Curie");
        when(personRepository.findByEmailIgnoreCase("marie.curie@siamois.fr")).thenReturn(Optional.empty());
        when(personRepository.findByNameIgnoreCaseAndLastnameIgnoreCase("Marie", "Curie"))
                .thenReturn(Optional.of(p));

        Person result = seeder.findOrCreatePerson("marie.curie@siamois.fr", null);

        assertSame(p, result);
        verify(personRepository, never()).save(any(Person.class));
    }

    @Test
    void findOrCreatePerson_usernameTaken_appendsFirstFreeSuffix() {
        when(personRepository.findByUsernameIgnoreCase("Marie.Curie")).thenReturn(Optional.of(new Person()));
        when(personRepository.findByUsernameIgnoreCase("Marie.Curie1")).thenReturn(Optional.of(new Person()));
        when(personRepository.save(any(Person.class))).thenAnswer(returnsFirstArg());

        Person result = seeder.findOrCreatePerson("Marie Curie", null);

        assertEquals("Marie.Curie2", result.getUsername());
    }

    @Test
    void findOrCreatePerson_usernameTooLong_isTruncated() {
        when(personRepository.save(any(Person.class))).thenAnswer(returnsFirstArg());

        Person result = seeder.findOrCreatePerson("Abcdefghijklmnopqrstuvwxyz Longlastname", null);

        assertEquals("Abcdefghijklmnopqrstuvwxyz.Longl", result.getUsername());
        assertEquals(Person.USERNAME_MAX_LENGTH, result.getUsername().length());
    }

    @Test
    void findOrCreatePerson_truncatedUsernameTaken_shortensBaseToFitSuffix() {
        when(personRepository.findByUsernameIgnoreCase("Abcdefghijklmnopqrstuvwxyz.Longl"))
                .thenReturn(Optional.of(new Person()));
        when(personRepository.save(any(Person.class))).thenAnswer(returnsFirstArg());

        Person result = seeder.findOrCreatePerson("Abcdefghijklmnopqrstuvwxyz Longlastname", null);

        assertEquals("Abcdefghijklmnopqrstuvwxyz.Long1", result.getUsername());
    }

    @Test
    void findOrCreatePerson_allSuffixesTaken_fallsBackToBaseUsername() {
        when(personRepository.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.of(new Person()));
        when(personRepository.save(any(Person.class))).thenAnswer(returnsFirstArg());

        Person result = seeder.findOrCreatePerson("Marie Curie", null);

        assertEquals("Marie.Curie", result.getUsername());
        verify(personRepository, times(1000)).findByUsernameIgnoreCase(anyString());
    }

    @ParameterizedTest
    @MethodSource("incompleteMembershipArguments")
    void ensureMemberOfInstitution_missingPersonOrInstitutionOrId_doesNotAssign(Person person, Institution institution) {
        seeder.ensureMemberOfInstitution(person, institution);

        verifyNoInteractions(personProfileAssignmentService);
    }

    static Stream<Arguments> incompleteMembershipArguments() {
        return Stream.of(
                Arguments.of(null, institution(10L)),
                Arguments.of(person(42L, "Pascal", "Gibut"), null),
                Arguments.of(person(null, "Pascal", "Gibut"), institution(10L)),
                Arguments.of(person(42L, "Pascal", "Gibut"), institution(null))
        );
    }

    @ParameterizedTest
    @MethodSource("systemPersons")
    void ensureMemberOfInstitution_systemPersonVariants_doesNotAssign(Person systemPerson) {
        seeder.ensureMemberOfInstitution(systemPerson, institution(10L));

        verifyNoInteractions(personProfileAssignmentService);
    }

    static Stream<Person> systemPersons() {
        Person byUsername = person(1L, "Jean", "Dupont");
        byUsername.setUsername("SIAMOIS.SYSTEM");

        Person bySchemaUsername = person(2L, "Jean", "Dupont");
        bySchemaUsername.setUsername(ImportSchema.SIAMOIS_SYSTEM.toUpperCase());

        Person byNames = person(3L, "Siamois", "SYSTEM");

        Person byEmail = person(4L, "Jean", "Dupont");
        byEmail.setEmail(ImportSchema.SIAMOIS_SYSTEM);

        return Stream.of(byUsername, bySchemaUsername, byNames, byEmail);
    }

    @Test
    void ensureMemberOfInstitution_siamoisFirstnameOnly_assigns() {
        Institution inst = institution(10L);
        Person p = person(42L, "Siamois", "Dupont");

        seeder.ensureMemberOfInstitution(p, inst);

        verify(personProfileAssignmentService).assignOrganizationMemberProfile(inst, p);
    }

    @Test
    void prefetchByNameLastName_ignoresInvalidEntriesAndIncompletePersons() {
        Person incomplete = new Person();
        incomplete.setName("Marie");
        incomplete.setUsername("marie");
        Person nameless = new Person();
        nameless.setLastname("Curie");
        nameless.setUsername("curie");
        Person complete = person(42L, "Marie", "Curie");
        when(personRepository.findAllByNameIgnoreCaseIn(Set.of("Marie")))
                .thenReturn(List.of(incomplete, nameless, complete));
        when(personRepository.findByEmailIgnoreCase("unknown@siamois.fr")).thenReturn(Optional.empty());

        Map<String, Person> cache = seeder.prefetchByNameLastName(
                Arrays.asList(null, "   ", "Pascal", " Marie Curie ", "unknown@siamois.fr"));

        assertThat(cache).containsOnly(Map.entry("marie|curie", complete));
    }

    @Test
    void prefetchByNameLastName_emailsOnly_lowercasesKeyAndSkipsNameQuery() {
        Person p = person(42L, "Pascal", "Gibut");
        when(personRepository.findByEmailIgnoreCase("Pascal.Gibut@Siamois.fr")).thenReturn(Optional.of(p));

        Map<String, Person> cache = seeder.prefetchByNameLastName(List.of("Pascal.Gibut@Siamois.fr"));

        assertThat(cache).containsOnly(Map.entry("pascal.gibut@siamois.fr", p));
        verify(personRepository, never()).findAllByNameIgnoreCaseIn(any());
    }

    @Test
    void resolveCached_withoutInstitution_returnsCachedPerson() {
        Person p = person(42L, "Pascal", "Gibut");
        Map<String, Person> cache = new HashMap<>(Map.of("pascal|gibut", p));

        Person result = seeder.resolveCached(cache, " PASCAL Gibut ");

        assertSame(p, result);
        verifyNoInteractions(personRepository, personProfileAssignmentService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "Pascal"})
    void resolveCached_unresolvableKey_returnsNull(String nameLastName) {
        Map<String, Person> cache = new HashMap<>();

        assertNull(seeder.resolveCached(cache, nameLastName, institution(10L)));
        assertThat(cache).isEmpty();
        verifyNoInteractions(personRepository, personProfileAssignmentService);
    }

    @Test
    void resolveCached_alreadyAssignedToInstitution_returnsWithoutAssigning() {
        Person p = person(42L, "Pascal", "Gibut");
        Map<String, Person> cache = new HashMap<>(Map.of("pascal|gibut" + PersonSeeder.INST + 10L, p));

        Person result = seeder.resolveCached(cache, "Pascal Gibut", institution(10L));

        assertSame(p, result);
        verifyNoInteractions(personRepository, personProfileAssignmentService);
    }

    @Test
    void resolveCached_cachedButNotAssigned_assignsAndCachesInstitutionKey() {
        Institution inst = institution(10L);
        Person p = person(42L, "Pascal", "Gibut");
        Map<String, Person> cache = new HashMap<>(Map.of("pascal|gibut", p));

        Person result = seeder.resolveCached(cache, "Pascal Gibut", inst);

        assertSame(p, result);
        verify(personProfileAssignmentService).assignOrganizationMemberProfile(inst, p);
        verifyNoInteractions(personRepository);
        assertThat(cache).containsEntry("pascal|gibut" + PersonSeeder.INST + 10L, p);
    }

    @Test
    void resolveCached_cachedWithInstitutionWithoutId_doesNotCacheInstitutionKey() {
        Person p = person(42L, "Pascal", "Gibut");
        Map<String, Person> cache = new HashMap<>(Map.of("pascal|gibut", p));

        Person result = seeder.resolveCached(cache, "Pascal Gibut", institution(null));

        assertSame(p, result);
        assertThat(cache).containsOnlyKeys("pascal|gibut");
        verifyNoInteractions(personProfileAssignmentService);
    }

    @Test
    void resolveCached_notCachedWithInstitutionWithoutId_cachesPlainKeyOnly() {
        Person p = person(42L, "Pascal", "Gibut");
        when(personRepository.findByNameIgnoreCaseAndLastnameIgnoreCase("Pascal", "Gibut"))
                .thenReturn(Optional.of(p));
        Map<String, Person> cache = new HashMap<>();

        Person result = seeder.resolveCached(cache, "Pascal Gibut", institution(null));

        assertSame(p, result);
        assertThat(cache).containsOnly(Map.entry("pascal|gibut", p));
        verifyNoInteractions(personProfileAssignmentService);
    }

    @Test
    void resolveCached_notCachedEmail_resolvesAndCachesLowercasedKey() {
        Person p = person(42L, "Marie", "Curie");
        when(personRepository.findByEmailIgnoreCase("Marie.Curie@Siamois.fr")).thenReturn(Optional.of(p));
        Map<String, Person> cache = new HashMap<>();

        Person result = seeder.resolveCached(cache, "Marie.Curie@Siamois.fr");

        assertSame(p, result);
        assertThat(cache).containsOnly(Map.entry("marie.curie@siamois.fr", p));
    }

    @Test
    void seed_existingByEmail_skipsNameLookup() {
        Person p = person(42L, "Marie", "Curie");
        when(personRepository.findByEmailIgnoreCase("marie.curie@siamois.fr")).thenReturn(Optional.of(p));

        Map<String, Person> res = seeder.seed(List.of(
                new PersonSeeder.PersonSpec("marie.curie@siamois.fr", "Marie", "Curie", "mcurie")));

        assertThat(res).containsOnly(Map.entry("marie.curie@siamois.fr", p));
        verify(personRepository, never()).findByNameIgnoreCaseAndLastnameIgnoreCase(anyString(), anyString());
        verify(personRepository, never()).save(any(Person.class));
    }

    @Test
    void seed_blankEmail_skipsEmailLookupAndSavesSpecValues() {
        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        when(personRepository.save(captor.capture())).thenAnswer(returnsFirstArg());

        seeder.seed(List.of(new PersonSeeder.PersonSpec("  ", "Marie", "Curie", "mcurie")));

        assertThat(captor.getValue())
                .extracting(Person::getEmail, Person::getName, Person::getLastname, Person::getUsername)
                .containsExactly("  ", "Marie", "Curie", "mcurie");
        verify(personRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void seed_failure_wrapsExceptionWithLineNumberAndEmail() {
        RuntimeException cause = new RuntimeException("boom");
        when(personRepository.save(any(Person.class)))
                .thenAnswer(returnsFirstArg())
                .thenThrow(cause);
        List<PersonSeeder.PersonSpec> specs = List.of(
                new PersonSeeder.PersonSpec("a@siamois.fr", "Anne", "Martin", "amartin"),
                new PersonSeeder.PersonSpec("b@siamois.fr", "Bruno", "Petit", "bpetit"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> seeder.seed(specs));

        assertEquals("[Personne ligne 2] 'b@siamois.fr' : boom", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    private static Person person(Long id, String name, String lastname) {
        Person p = new Person();
        p.setId(id);
        p.setName(name);
        p.setLastname(lastname);
        return p;
    }

    private static Institution institution(Long id) {
        Institution inst = new Institution();
        inst.setId(id);
        return inst;
    }
}
