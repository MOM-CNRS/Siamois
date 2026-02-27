package fr.siamois.domain.services.person.verifier;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.auth.InvalidPasswordException;
import fr.siamois.dto.entity.PersonDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class PasswordVerifier  {

    public void verify(String password) throws InvalidPasswordException {
        passwordSizeIsValid(password);
    }

    private static void passwordSizeIsValid(String password) throws InvalidPasswordException {
        if (StringUtils.isBlank(password)) throw new InvalidPasswordException("Password cannot be empty.");
        if (password.length() < 8) throw new InvalidPasswordException("Password must be at least 8 characters long.");
        if (password.length() > Person.PASSWORD_MAX_LENGTH) throw new InvalidPasswordException("Password should not exceed " + Person.PASSWORD_MAX_LENGTH + " characters.");
    }
}
