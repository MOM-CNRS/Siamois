package fr.siamois.domain.services.person.verifier;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.auth.InvalidUsernameException;
import fr.siamois.domain.models.exceptions.auth.UserAlreadyExistException;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class UsernameVerifierTest {

    @Mock
    private PersonRepository personRepository;

    private UsernameVerifier usernameVerifier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        usernameVerifier = new UsernameVerifier(personRepository);
    }

    @Test
    void verify_shouldThrowInvalidUsername_whenUsernameIsInvalid() {
        PersonDTO person = new PersonDTO();
        person.setUsername("invalid username");

        assertThrows(InvalidUsernameException.class, () -> usernameVerifier.verify(person));
    }

    @Test
    void verify_shouldThrowUserAlreadyExist_whenUsernameAlreadyExists() {
        PersonDTO person = new PersonDTO();
        person.setId(-1L);
        person.setUsername("existingUser");

        when(personRepository.findByUsernameIgnoreCase("existingUser")).thenReturn(Optional.of(new Person()));

        assertThrows(UserAlreadyExistException.class, () -> usernameVerifier.verify(person));
    }

    @Test
    void verify_shouldNotThrowException_whenUsernameIsValidAndUnique() throws InvalidUsernameException, UserAlreadyExistException {
        PersonDTO person = new PersonDTO();
        person.setUsername("uniqueUser");

        when(personRepository.findByUsernameIgnoreCase("uniqueUser")).thenReturn(Optional.empty());

        usernameVerifier.verify(person);
    }
}