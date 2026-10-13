package fr.siamois.ui.bean;

import fr.siamois.domain.models.auth.token.PersonActionToken;
import fr.siamois.domain.models.auth.token.PersonActionType;
import fr.siamois.domain.models.exceptions.auth.InvalidEmailException;
import fr.siamois.domain.services.auth.PersonActionTokenService;
import fr.siamois.domain.services.person.PersonService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Backs the public page reached from the e-mailed link: the user explicitly accepts or refuses the
 * change of e-mail address (opening the link alone changes nothing, e.g. when a mail scanner
 * prefetches it).
 */
@Slf4j
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ConfirmEmailBean {

    public enum State { PENDING, ACCEPTED, REFUSED, FAILED }

    private final PersonService personService;
    private final PersonActionTokenService tokenService;

    private String token;
    @Getter
    private String newEmail;
    @Getter
    private State state = State.PENDING;

    public void init(String token, String newEmail) {
        this.token = token;
        this.newEmail = newEmail;
        this.state = State.PENDING;
    }

    public boolean isPending() {
        return state == State.PENDING;
    }

    public boolean isAccepted() {
        return state == State.ACCEPTED;
    }

    public boolean isRefused() {
        return state == State.REFUSED;
    }

    public boolean isFailed() {
        return state == State.FAILED;
    }

    public void accept() {
        Optional<PersonActionToken> opt = findToken();
        if (opt.isEmpty()) {
            state = State.FAILED;
            return;
        }
        try {
            personService.updateEmail(opt.get().getPerson().getId(), opt.get().getNewEmail());
            state = State.ACCEPTED;
        } catch (InvalidEmailException e) {
            // The address was taken (or became invalid) since the request.
            log.warn("E-mail change refused: {}", e.getMessage());
            state = State.FAILED;
        }
        tokenService.delete(opt.get());
    }

    public void refuse() {
        findToken().ifPresent(tokenService::delete);
        state = State.REFUSED;
    }

    private Optional<PersonActionToken> findToken() {
        return token == null ? Optional.empty() : tokenService.findValid(token, PersonActionType.EMAIL_CHANGE);
    }
}
