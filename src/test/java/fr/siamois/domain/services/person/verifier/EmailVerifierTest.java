package fr.siamois.domain.services.person.verifier;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.auth.EmailAlreadyExistException;
import fr.siamois.domain.models.exceptions.auth.InvalidEmailException;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerifierTest {

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private EmailVerifier emailVerifier;



    @Test
    void verify_shouldNotThrowException_whenEmailIsValid() throws InvalidEmailException {
        PersonDTO person = new PersonDTO();
        person.setEmail("valid.email@example.com");

        emailVerifier.verify(person);
    }

    @Test
    void verify_shouldThrowEmailAlreadyExistException_whenEmailAlreadyExists() {
        PersonDTO person = new PersonDTO();
        person.setEmail("existing.email@example.com");
        emailVerifier.isForCreation = true;

        when(personRepository.findByEmailIgnoreCase("existing.email@example.com")).thenReturn(Optional.of(new Person()));

        assertThrows(EmailAlreadyExistException.class, () -> emailVerifier.verify(person));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-email", ""})
    void verify_shouldThrowInvalidEmailException_forInvalidEmails(String email) {
        PersonDTO person = new PersonDTO();

        person.setEmail(email);

        assertThrows(InvalidEmailException.class, () -> emailVerifier.verify(person));
    }
}