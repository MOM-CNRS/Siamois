package fr.siamois.domain.services.auth;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;

/**
 * Service for managing pending persons. Pending persons are users who have been invited to register but have not yet completed the registration process.
 */
@Service
@RequiredArgsConstructor
public class PendingPersonService {

    private final PendingPersonRepository pendingPersonRepository;
    private final SecureRandom random = new SecureRandom();

    public static final int MAX_GENERATION = 3000;
    public static final int TOKEN_LENGTH = 20;
    private static final String ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final HttpServletRequest httpServletRequest;

    /**
     * Generate a random token for the pending person.
     *
     * @return a random token
     */
    String generateToken() {
        StringBuilder token;
        int attempts = 0;

        do {
            attempts++;
            token = new StringBuilder();
            for (int i = 0; i < TOKEN_LENGTH; i++) {
                int randomIndex = random.nextInt(ALLOWED_CHARS.length());
                token.append(ALLOWED_CHARS.charAt(randomIndex));
            }
        } while (attempts < MAX_GENERATION && pendingPersonRepository.existsByRegisterToken(token.toString()));

        if (attempts == MAX_GENERATION) {
            throw new IllegalStateException("Unable to generate a unique pending person token after " + MAX_GENERATION + " attempts");
        }

        return token.toString();
    }

    /**
     * Generate the invitation link for the pending person.
     *
     * @param pendingPerson the pending person
     * @return the invitation link
     */
    String invitationLink(PendingPerson pendingPerson) {
        String domain = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() +
                (isNotCommonHttpPort() ? ":" + httpServletRequest.getServerPort() : "");
        return String.format("%s%s/register/%s", domain, httpServletRequest.getContextPath(), pendingPerson.getRegisterToken());
    }

    private boolean isNotCommonHttpPort() {
        return httpServletRequest.getServerPort() != 80 && httpServletRequest.getServerPort() != 443;
    }

    /**
     * Delete a pending person from the database.
     *
     * @param pendingPerson the pending person to delete
     */
    public void delete(PendingPerson pendingPerson) {
        pendingPersonRepository.delete(pendingPerson);
    }

    /**
     * Find a pending person by their registration token.
     *
     * @param token the registration token of the pending person
     * @return an Optional containing the pending person if found, or empty if not found
     */
    public Optional<PendingPerson> findByToken(String token) {
        return pendingPersonRepository.findByRegisterToken(token);
    }

    public void deleteByPerson(PersonDTO person) {
        pendingPersonRepository.deleteByDisabledPersonId(person.getId());
    }
}
