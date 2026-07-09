package fr.siamois.ui.bean;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.exceptions.auth.EmailAlreadyExistException;
import fr.siamois.domain.models.exceptions.auth.InvalidEmailException;
import fr.siamois.domain.models.exceptions.auth.InvalidNameException;
import fr.siamois.domain.models.exceptions.auth.InvalidPasswordException;
import fr.siamois.domain.models.exceptions.auth.InvalidUsernameException;
import fr.siamois.domain.models.exceptions.auth.UserAlreadyExistException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.PersonMapper;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

        if (StringUtils.isBlank(email) || StringUtils.isBlank(password) || StringUtils.isBlank(confirmPassword)) {
            addError("register.error.fields");
            return;
        }

        if (!password.equals(confirmPassword)) {
            addError("register.error.password.match");
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
        } catch (EmailAlreadyExistException e) {
            addError("register.error.email.alreadyexists", email);
        } catch (InvalidEmailException e) {
            addError("register.error.email");
        } catch (InvalidUsernameException e) {
            addError("register.error.username");
        } catch (InvalidNameException e) {
            addError("register.error.name");
        } catch (InvalidPasswordException e) {
            addError("register.error.password");
        } catch (UserAlreadyExistException e) {
            addError("register.error.username.alreadyexists", username);
        }

    }

    /** Adds a page-level error message shown by the {@code <p:messages>} component of the register form. */
    private void addError(String messageCode, Object... args) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, langBean.msg(messageCode, args), null));
    }

}
