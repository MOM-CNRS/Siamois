package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.dataimport.ImportSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
}
