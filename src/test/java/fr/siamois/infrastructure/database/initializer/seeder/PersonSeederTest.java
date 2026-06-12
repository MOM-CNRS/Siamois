package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonSeederTest {

    @Mock
    PersonRepository personRepository;

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

}