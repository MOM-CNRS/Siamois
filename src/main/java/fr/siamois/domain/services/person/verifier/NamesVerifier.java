package fr.siamois.domain.services.person.verifier;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.dto.entity.PersonDTO;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class NamesVerifier extends PersonDataVerifier {

    private void validateNameLength(String name, String fieldName) throws InvalidNameException {
        if (name != null && name.length() > Person.NAME_MAX_LENGTH) {
            throw new InvalidNameException(fieldName + " should be at most " + Person.NAME_MAX_LENGTH + " characters.");
        }
    }

    @Override
    public void verify(PersonDTO person) throws InvalidUsernameException, UserAlreadyExistException, InvalidEmailException, InvalidPasswordException, InvalidNameException {
        validateNameLength(person.getName(), "Name");
        validateNameLength(person.getLastname(), "Last name");
    }
}
