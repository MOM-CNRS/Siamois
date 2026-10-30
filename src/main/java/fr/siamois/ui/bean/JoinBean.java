package fr.siamois.ui.bean;

import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.domain.models.exceptions.institution.FailedInstitutionSaveException;
import fr.siamois.domain.models.exceptions.institution.InstitutionAlreadyExistException;
import fr.siamois.domain.services.auth.InvitationLinkService;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.config.handler.LoginSuccessHandler;
import jakarta.faces.context.ExternalContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import java.io.IOException;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

/** Sign-up form reached through an invitation link: creates the account and its personal organization. */
@Slf4j
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
@Setter
public class JoinBean {

    private final InvitationLinkService invitationLinkService;
    private final PersonService personService;
    private final LoginSuccessHandler loginSuccessHandler;
    private final LangBean langBean;
    private final RedirectBean redirectBean;

    private String token;
    private String email;
    private String password;
    private String confirmPassword;
    private String firstName;
    private String lastName;

    public JoinBean(InvitationLinkService invitationLinkService, PersonService personService,
                    LoginSuccessHandler loginSuccessHandler, LangBean langBean, RedirectBean redirectBean) {
        this.invitationLinkService = invitationLinkService;
        this.personService = personService;
        this.loginSuccessHandler = loginSuccessHandler;
        this.langBean = langBean;
        this.redirectBean = redirectBean;
    }

    public void init(String token) {
        reset();
        this.token = token;
    }

    private void reset() {
        token = null;
        email = null;
        password = null;
        confirmPassword = null;
        firstName = null;
        lastName = null;
    }

    public void register() {
        if (StringUtils.isAnyBlank(email, password, confirmPassword)) {
            addError("register.error.fields");
            return;
        }
        if (!password.equals(confirmPassword)) {
            addError("register.error.password.match");
            return;
        }

        PersonDTO person = new PersonDTO();
        person.setEmail(email);
        person.setName(firstName);
        person.setLastname(lastName);

        try {
            PersonDTO created = invitationLinkService.register(token, person, password);
            reset();
            signIn(personService.findById(created.getId()));
        } catch (InvitationLinkUnavailableException e) {
            log.warn("Sign-up on an unusable invitation link");
            reset();
            redirectBean.redirectTo("/error/404");
        } catch (EmailAlreadyExistException e) {
            addError("register.error.email.alreadyexists", email);
        } catch (InvalidEmailException e) {
            addError("register.error.email");
        } catch (InvalidUsernameException e) {
            // The username is generated, never typed: this is not something the person can fix.
            log.error("Generated username rejected during sign-up", e);
            addError("common.error.internal");
        } catch (InvalidNameException e) {
            addError("register.error.name");
        } catch (InvalidPasswordException e) {
            addError("register.error.password");
        } catch (UserAlreadyExistException e) {
            addError("register.error.email.alreadyexists", email);
        } catch (IOException e) {
            log.error("Could not log the new account in", e);
            redirectBean.redirectTo("/login");
        } catch (InstitutionAlreadyExistException | FailedInstitutionSaveException e) {
            log.error("Personal organization creation failed during sign-up", e);
            addError("join.error.organization");
        }
    }

    /** Logs the freshly created person in, as the login form would, and sends them where it would. */
    private void signIn(Person person) throws IOException {
        ExternalContext external = FacesContext.getCurrentInstance().getExternalContext();
        HttpServletRequest request = (HttpServletRequest) external.getRequest();
        HttpServletResponse response = (HttpServletResponse) external.getResponse();

        request.changeSessionId();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(person, null, person.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        loginSuccessHandler.onAuthenticationSuccess(request, response, authentication);
        FacesContext.getCurrentInstance().responseComplete();
    }

    private void addError(String messageCode, Object... args) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, langBean.msg(messageCode, args), null));
    }
}
