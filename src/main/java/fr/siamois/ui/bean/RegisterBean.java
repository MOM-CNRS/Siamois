package fr.siamois.ui.bean;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.exceptions.auth.InvalidPasswordException;
import fr.siamois.domain.models.exceptions.auth.InvalidUserInformationException;
import fr.siamois.domain.models.exceptions.auth.UserAlreadyExistException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.PersonDTO;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;

@Slf4j
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
@Setter
public class RegisterBean {

    private final PersonService personService;
    private final InstitutionService institutionService;
    private final LangBean langBean;
    private final RedirectBean redirectBean;
    private final PendingPersonService pendingPersonService;
    private String email;
    private String password;
    private String confirmPassword;
    private Institution institution;
    private String firstName;
    private String lastName;
    private String username;

    /** ID of the already-created (disabled) account waiting for its password, null when the account does not exist yet. */
    private Long existingPersonId;

    public RegisterBean(PersonService personService,
                        InstitutionService institutionService,
                        LangBean langBean,
                        RedirectBean redirectBean, PendingPersonService pendingPersonService) {
        this.personService = personService;
        this.institutionService = institutionService;
        this.langBean = langBean;
        this.redirectBean = redirectBean;
        this.pendingPersonService = pendingPersonService;
    }

    public void reset() {
        email = null;
        password = null;
        confirmPassword = null;
        institution = null;
        firstName = null;
        lastName = null;
        username = null;
        existingPersonId = null;
    }

    public void init(PendingPerson pendingPerson) {
        reset();
        this.email = pendingPerson.getEmail();

        Optional<Person> existingPerson = personService.findByEmail(pendingPerson.getEmail());
        if (existingPerson.isPresent()) {
            Person person = existingPerson.get();
            existingPersonId = person.getId();
            firstName = person.getName();
            lastName = person.getLastname();
            username = person.getUsername();
        }
    }

    /** @return true when the invited person's account already exists and only needs a password to be enabled */
    public boolean isExistingAccount() {
        return existingPersonId != null;
    }

    public void register() {

        if (email == null || password == null || confirmPassword == null) {
            log.trace("Email and password are not set");
            return;
        }

        if (!password.equals(confirmPassword)) {
            log.trace("Password and confirm password are not the same");
            displayErrorMessage(langBean, "userDialog.error.password.match");
            return;
        }

        if (isExistingAccount()) {
            activateExistingAccount();
        } else {
            createAccount();
        }
    }

    /** Sets the created password on the invited person's account and enables it. */
    private void activateExistingAccount() {
        try {
            personService.activatePerson(existingPersonId, password);
            log.trace("Person activated");

            reset();
            redirectBean.redirectTo("/login");
        } catch (InvalidPasswordException e) {
            log.trace("Invalid password");
            displayErrorMessage(langBean, "userDialog.error.password");
        }
    }

    private void createAccount() {
        PersonDTO person = new PersonDTO();
        person.setEmail(email);
        person.setName(firstName);
        person.setLastname(lastName);
        person.setUsername(username);

        try {
            personService.createPerson(person, password);
            log.trace("Person created");

            reset();
            redirectBean.redirectTo("/login");

        } catch (InvalidUserInformationException e) {
            log.trace("Person could not be created");
        } catch (UserAlreadyExistException e) {
            log.trace("User already exists");
        }
    }

}
