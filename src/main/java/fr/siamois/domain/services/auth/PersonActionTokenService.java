package fr.siamois.domain.services.auth;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.auth.token.PersonActionToken;
import fr.siamois.domain.models.auth.token.PersonActionType;
import fr.siamois.infrastructure.database.repositories.person.PersonActionTokenRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Manages the single-use tokens sent by e-mail to reset a password or confirm a new e-mail address.
 * A person holds at most one active token per {@link PersonActionType}: asking again replaces the
 * previous one (and invalidates its link).
 */
@Service
@RequiredArgsConstructor
public class PersonActionTokenService {

    public static final int TOKEN_LENGTH = 32;
    public static final Duration PASSWORD_RESET_VALIDITY = Duration.ofHours(1);
    public static final Duration EMAIL_CHANGE_VALIDITY = Duration.ofHours(24);
    /** Minimum delay between two e-mails of the same type for a person. */
    public static final Duration MIN_DELAY_BETWEEN_REQUESTS = Duration.ofMinutes(1);
    private static final String ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final PersonActionTokenRepository tokenRepository;
    private final PersonRepository personRepository;
    private final HttpServletRequest httpServletRequest;
    private final SecureRandom random = new SecureRandom();

    /**
     * Tells whether a new token can be issued, i.e. the previous one of the same type was created
     * more than {@link #MIN_DELAY_BETWEEN_REQUESTS} ago (or there is none).
     */
    public boolean canIssue(Long personId, PersonActionType type) {
        return tokenRepository.findByPersonIdAndType(personId, type)
                .map(t -> t.getCreationDate().plus(MIN_DELAY_BETWEEN_REQUESTS).isBefore(OffsetDateTime.now(ZoneOffset.UTC)))
                .orElse(true);
    }

    /**
     * Issues a fresh token for the person, replacing any previous token of the same type.
     *
     * @param personId the person the token belongs to
     * @param type     the operation the token authorises
     * @param newEmail the address to switch to (only for {@link PersonActionType#EMAIL_CHANGE})
     * @return the saved token
     */
    @Transactional
    public PersonActionToken issue(Long personId, PersonActionType type, String newEmail) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new EntityNotFoundException("Person not found"));
        tokenRepository.deleteByPersonIdAndType(personId, type);
        tokenRepository.flush();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PersonActionToken token = new PersonActionToken();
        token.setPerson(person);
        token.setType(type);
        token.setToken(generateToken());
        token.setNewEmail(type == PersonActionType.EMAIL_CHANGE ? newEmail : null);
        token.setCreationDate(now);
        token.setExpirationDate(now.plus(validityOf(type)));
        return tokenRepository.save(token);
    }

    /**
     * @return the token if it exists for that type and has not expired
     */
    @Transactional(readOnly = true)
    public Optional<PersonActionToken> findValid(String token, PersonActionType type) {
        return tokenRepository.findByTokenAndType(token, type)
                .filter(t -> t.getExpirationDate().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    public void delete(PersonActionToken token) {
        tokenRepository.delete(token);
    }

    /**
     * Builds the absolute link the e-mail recipient has to follow.
     */
    public String link(PersonActionToken token) {
        String path = token.getType() == PersonActionType.PASSWORD_RESET ? "reset-password" : "confirm-email";
        int port = httpServletRequest.getServerPort();
        String domain = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName()
                + (port != 80 && port != 443 ? ":" + port : "");
        return domain + httpServletRequest.getContextPath() + "/" + path + "/" + token.getToken();
    }

    public static Duration validityOf(PersonActionType type) {
        return type == PersonActionType.PASSWORD_RESET ? PASSWORD_RESET_VALIDITY : EMAIL_CHANGE_VALIDITY;
    }

    String generateToken() {
        String candidate;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < TOKEN_LENGTH; i++) {
                sb.append(ALLOWED_CHARS.charAt(random.nextInt(ALLOWED_CHARS.length())));
            }
            candidate = sb.toString();
        } while (tokenRepository.existsByToken(candidate));
        return candidate;
    }
}
