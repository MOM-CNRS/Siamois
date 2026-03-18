package fr.siamois.domain.services.person.verifier;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.auth.InvalidUsernameException;
import fr.siamois.domain.models.exceptions.auth.UserAlreadyExistException;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(1)
public class UsernameVerifier extends PersonDataVerifier {
    private final PersonRepository personRepository;

    public UsernameVerifier(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Override
    public void verify(PersonDTO person) throws InvalidUsernameException, UserAlreadyExistException {
        String username = person.getUsername();

        usernameHasValidLength(username);
        usernameHasValidChars(username);
        usernameDoesNotExist(person.getId(), username);
    }

    private void usernameDoesNotExist(Long id, String username) throws UserAlreadyExistException {
        if (id == null || id < 0) {
            Optional<Person> optPerson = personRepository.findByUsernameIgnoreCase(username);
            if (optPerson.isPresent()) throw new UserAlreadyExistException("Username already exists.");
        }
    }

    private static void usernameHasValidLength(String username) throws InvalidUsernameException {
        if (StringUtils.isBlank(username)) throw new InvalidUsernameException("Username cannot be empty.");
        if (username.length() > Person.USERNAME_MAX_LENGTH) throw new InvalidUsernameException("Username should not exceed " + Person.USERNAME_MAX_LENGTH + " characters.");
    }

    private static void usernameHasValidChars(String username) throws InvalidUsernameException {
        Pattern pattern = Pattern.compile("^[a-zA-Z0-9.]+$");
        Matcher matcher = pattern.matcher(username);

        if (!matcher.find()) throw new InvalidUsernameException("Username must contain only letters, numbers and dots.");
    }
}
