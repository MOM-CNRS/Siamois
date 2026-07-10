package fr.siamois.ui.bean;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.exceptions.auth.InvalidUserInformationException;
import fr.siamois.domain.models.exceptions.auth.UserAlreadyExistException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.PersonMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

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
    private final PersonMapper personMapper;
    private PersonDTO person;
    private String email;
    private String password;
    private String confirmPassword;
    private Institution institution;
    private String firstName;
    private String lastName;
    private String username;

    public RegisterBean(PersonService personService,
                        InstitutionService institutionService,
                        LangBean langBean,
                        RedirectBean redirectBean, PendingPersonService pendingPersonService, PersonMapper personMapper) {
        this.personService = personService;
        this.institutionService = institutionService;
        this.langBean = langBean;
        this.redirectBean = redirectBean;
        this.pendingPersonService = pendingPersonService;
        this.personMapper = personMapper;
    }

    public void reset() {
        email = null;
        password = null;
        confirmPassword = null;
        institution = null;
        firstName = null;
        lastName = null;
        username = null;
    }

    public void init(PendingPerson pendingPerson) {
        reset();
        this.person = personMapper.convert(pendingPerson.getDisabledPerson());
        assert person != null;
        this.email = person.getEmail();
        this.firstName = person.getName();
        this.lastName = person.getName();
        this.username = person.getUsername();
    }

    public void register() {

        if (email == null || password == null || confirmPassword == null) {
            log.trace("Email and password are not set");
            return;
        }

        if (!password.equals(confirmPassword)) {
            log.trace("Password and confirm password are not the same");
            return;
        }

        person.setEmail(email);
        person.setName(firstName);
        person.setLastname(lastName);
        person.setUsername(username);

        try {
            personService.enableAndUpdatePerson(person, password);
            pendingPersonService.deleteByPerson(person);

            reset();
            redirectBean.redirectTo("/login");

        } catch (InvalidUserInformationException e) {
            log.trace("Person could not be created");
        } catch (UserAlreadyExistException e) {
            log.trace("User already exists");
        }

    }

}
