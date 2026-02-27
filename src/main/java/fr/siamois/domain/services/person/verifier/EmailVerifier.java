package fr.siamois.domain.services.person.verifier;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.auth.EmailAlreadyExistException;
import fr.siamois.domain.models.exceptions.auth.InvalidEmailException;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(2)
public class EmailVerifier extends PersonDataVerifier {

    private final PersonRepository personRepository;
    public EmailVerifier(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Override
    public void verify(PersonDTO person) throws InvalidEmailException {
        String email = person.getEmail();

        emailUsesValidChars(email);
        checkMailLength(email);
        checkMailExistance(email);
    }

    private static void emailUsesValidChars(String email) throws InvalidEmailException {
        Pattern emailPattern = Pattern.compile("^[a-zA-Z0-9._+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
        Matcher emailMatcher = emailPattern.matcher(email);
        if (!emailMatcher.find()) throw new InvalidEmailException("Email is not valid.");
    }

    private void checkMailExistance(String email) throws InvalidEmailException {
        boolean emailExist = personRepository.findByEmailIgnoreCase(email).isPresent();
        if (emailExist && isForCreation) {
            throw new EmailAlreadyExistException("Email already exists.");
        }
    }

    private static void checkMailLength(String email) throws InvalidEmailException {
        if (StringUtils.isBlank(email)) throw new InvalidEmailException("Email cannot be empty.");
        String[] splitMail = email.split("@");
        if (splitMail[0].length() > Person.LOCAL_MAIL_MAX_LENGTH) throw new InvalidEmailException("Local part of the email should not exceed "
                + Person.LOCAL_MAIL_MAX_LENGTH + " characters.");
        if (splitMail[1].length() > Person.DOMAIN_MAIL_MAX_LENGTH) throw new InvalidEmailException("Domain part of the email should not exceed "
                + Person.DOMAIN_MAIL_MAX_LENGTH + " characters.");
    }

}
