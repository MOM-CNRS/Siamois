package fr.siamois.ui.bean;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.token.PersonActionToken;
import fr.siamois.domain.models.auth.token.PersonActionType;
import fr.siamois.domain.services.auth.PersonActionTokenService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.ui.email.AccountActionMailer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Backs the public "I lost my password" page. Whatever the address typed, the answer is the same,
 * so the page cannot be used to find out which addresses have an account.
 */
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
@Setter
@RequiredArgsConstructor
public class ForgotPasswordBean {

    private final PersonService personService;
    private final PersonActionTokenService tokenService;
    private final AccountActionMailer accountActionMailer;

    private String email;
    private boolean sent;

    public void init() {
        email = null;
        sent = false;
    }

    public void submit() {
        String address = StringUtils.trimToEmpty(email);
        Optional<Person> person = address.isEmpty() ? Optional.empty() : personService.findByEmail(address);
        if (person.isPresent() && person.get().isEnabled()
                && tokenService.canIssue(person.get().getId(), PersonActionType.PASSWORD_RESET)) {
            PersonActionToken token = tokenService.issue(person.get().getId(), PersonActionType.PASSWORD_RESET, null);
            accountActionMailer.send(token, person.get().getEmail());
        }
        sent = true;
    }
}
