package fr.siamois.domain.services.person.verifier;

import fr.siamois.domain.models.exceptions.auth.InvalidPasswordException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordVerifierTest {

    private final PasswordVerifier passwordVerifier = new PasswordVerifier();

    @Test
    void verify_shouldThrowInvalidPassword_whenPasswordIsInvalid() {


        assertThrows(InvalidPasswordException.class, () -> passwordVerifier.verify("short"));
    }

    @Test
    void verify_shouldNotThrowException_whenPasswordIsValid() throws InvalidPasswordException {

        passwordVerifier.verify("validPassword123");
    }
}