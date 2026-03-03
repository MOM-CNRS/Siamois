package fr.siamois.domain.services.person.verifier;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class NamesVerifierTest {

    private NamesVerifier namesVerifier;

    @BeforeEach
    void setUp() {
        namesVerifier = new NamesVerifier();
    }

    @Test
    void verify_shouldThrowInvalidNameException_whenNameIsTooLong() {
        PersonDTO person = new PersonDTO();
        person.setName("a".repeat(Person.NAME_MAX_LENGTH + 1));
        person.setLastname("ValidLastName");

        assertThrows(InvalidNameException.class, () -> namesVerifier.verify(person));
    }

    @Test
    void verify_shouldThrowInvalidNameException_whenLastNameIsTooLong() {
        PersonDTO person = new PersonDTO();
        person.setName("ValidName");
        person.setLastname("a".repeat(Person.NAME_MAX_LENGTH + 1));

        assertThrows(InvalidNameException.class, () -> namesVerifier.verify(person));
    }

    @Test
    void verify_shouldNotThrowException_whenNamesAreValid() throws UserAlreadyExistException, InvalidNameException, InvalidPasswordException, InvalidUsernameException, InvalidEmailException {
        PersonDTO person = new PersonDTO();
        person.setName("ValidName");
        person.setLastname("ValidLastName");

        namesVerifier.verify(person);
    }
}