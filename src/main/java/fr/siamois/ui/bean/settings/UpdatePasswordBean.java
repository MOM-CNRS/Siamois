package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.auth.InvalidPasswordException;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;

@Slf4j
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Component
@Getter
@Setter
@RequiredArgsConstructor
public class UpdatePasswordBean implements Serializable {

    private final transient PersonService personService;
    private final LangBean langBean;
    private PersonDTO authenticatedUser;
    private String nextUrl;

    private String newPassword;
    private String confirmNewPassword;

    public void init(PersonDTO authenticatedUser, String nextUrl) {
        this.authenticatedUser = authenticatedUser;
        this.nextUrl = nextUrl;
        newPassword = null;
        confirmNewPassword = null;
    }

    public void updatePassword() {
        if (newPassword != null && newPassword.equals(confirmNewPassword)) {
            try {
                personService.updatePassword(authenticatedUser, newPassword);
                FacesContext.getCurrentInstance().getExternalContext().redirect(nextUrl);
            } catch (InvalidPasswordException e) {
                displayErrorMessage("commons.error.user.password.invalid");
            } catch (IOException e) {
                log.error("Error redirecting after password update: {}", e.getMessage(), e);
            }
        } else {
            displayErrorMessage("commons.error.password.nomatch");
        }
    }

    private void displayErrorMessage(String key) {
        String title = langBean.msg("commons.message.state.error");
        String message = langBean.msg(key);
        FacesContext.getCurrentInstance().addMessage("passwordGrowl:growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, title, message));
    }

}
