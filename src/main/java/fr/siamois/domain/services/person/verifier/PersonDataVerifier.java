package fr.siamois.domain.services.person.verifier;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.dto.entity.PersonDTO;
import lombok.Setter;

/**
 * Interface for verifying the data of a Person.
 * This interface defines methods to validate various attributes of a Person object,
 * such as username, email, password, and name.
 */
@Setter
public abstract class PersonDataVerifier {

    protected boolean isForCreation = false;

    /**
     * Verifies the data of a Person and throws exceptions if any validation fails.
     * @param person the Person object to verify
     * @throws InvalidUsernameException if the username is invalid
     * @throws UserAlreadyExistException if a user with the same username already exists
     * @throws InvalidEmailException if the email is invalid or already in use
     * @throws InvalidPasswordException if the password does not meet the required criteria
     * @throws InvalidNameException if the name is invalid or does not meet the required criteria
     */
    public abstract void verify(PersonDTO person) throws InvalidUsernameException, UserAlreadyExistException, InvalidEmailException, InvalidPasswordException, InvalidNameException;

}
