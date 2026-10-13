package fr.siamois.ui.bean;

import fr.siamois.domain.models.auth.token.PersonActionToken;
import fr.siamois.domain.models.auth.token.PersonActionType;
import fr.siamois.domain.models.exceptions.auth.InvalidPasswordException;
import fr.siamois.domain.services.auth.PersonActionTokenService;
import fr.siamois.domain.services.person.PersonService;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Backs the public page where a user chooses a new password after following the e-mailed reset link.
 */
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
@Setter
@RequiredArgsConstructor
public class ResetPasswordBean {

    private final PersonService personService;
    private final PersonActionTokenService tokenService;
    private final LangBean langBean;
    private final RedirectBean redirectBean;

    private String token;
    private String password;
    private String confirmPassword;

    public void init(String token) {
        this.token = token;
        this.password = null;
        this.confirmPassword = null;
    }

    public void submit() {
        if (StringUtils.isBlank(password) || StringUtils.isBlank(confirmPassword)) {
            addError("register.error.fields");
            return;
        }
        if (!password.equals(confirmPassword)) {
            addError("register.error.password.match");
            return;
        }
        Optional<PersonActionToken> opt = token == null ? Optional.empty()
                : tokenService.findValid(token, PersonActionType.PASSWORD_RESET);
        if (opt.isEmpty()) {
            addError("resetPassword.error.expired");
            return;
        }
        try {
            personService.updatePassword(opt.get().getPerson().getId(), password);
        } catch (InvalidPasswordException e) {
            addError("register.error.password");
            return;
        }
        tokenService.delete(opt.get());
        init(null);
        redirectBean.redirectTo("/login");
    }

    private void addError(String messageCode) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, langBean.msg(messageCode), null));
    }
}
