package fr.siamois.domain.services.auth;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.mapper.PersonMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

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
    public static final int INVITATION_VALIDITY_DAYS = 3;
    private static final String ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final HttpServletRequest httpServletRequest;
    private final PersonMapper personMapper;

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
    public String invitationLink(PendingPerson pendingPerson) {
        String domain = httpServletRequest.getScheme() + "://" + httpServletRequest.getServerName() +
                (isNotCommonHttpPort() ? ":" + httpServletRequest.getServerPort() : "");
        return String.format("%s%s/register/%s", domain, httpServletRequest.getContextPath(), pendingPerson.getRegisterToken());
    }

    private boolean isNotCommonHttpPort() {
        return httpServletRequest.getServerPort() != 80 && httpServletRequest.getServerPort() != 443;
    }

    /**
     * Create an invitation for the given disabled person, or return the existing one if the person
     * has already been invited (the same token is then reused instead of invalidating the previous link).
     *
     * @param disabledPerson the disabled person to invite
     * @return the pending person carrying the registration token
     */
    public PendingPerson createOrGetInvitation(PersonDTO disabledPerson) {
        return pendingPersonRepository.findByDisabledPersonId(disabledPerson.getId())
                .orElseGet(() -> {
                    PendingPerson pendingPerson = new PendingPerson();
                    pendingPerson.setDisabledPerson(personMapper.invertConvert(disabledPerson));
                    pendingPerson.setRegisterToken(generateToken());
                    pendingPerson.setPendingInvitationExpirationDate(OffsetDateTime.now(ZoneOffset.UTC).plusDays(INVITATION_VALIDITY_DAYS));
                    return pendingPersonRepository.save(pendingPerson);
                });
    }

    /**
     * Check whether a person still has a pending invitation (i.e. was invited but has not completed
     * their registration yet).
     *
     * @param personId the id of the person
     * @return true if an invitation exists for this person
     */
    public boolean hasPendingInvitation(Long personId) {
        return pendingPersonRepository.existsByDisabledPersonId(personId);
    }

    /**
     * Among the given person ids, find the ones that still have a pending invitation.
     *
     * @param personIds the person ids to check
     * @return the subset of ids having a pending invitation
     */
    public Set<Long> findPersonIdsWithPendingInvitation(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return Set.of();
        }
        return pendingPersonRepository.findDisabledPersonIdsIn(personIds);
    }

    /**
     * Among the given person ids, find the ones whose pending invitation has passed its expiration date.
     * Expired invitations are kept in database (never auto-deleted); they can be renewed with
     * {@link #resendInvitation(PersonDTO)}.
     *
     * @param personIds the person ids to check
     * @return the subset of ids whose invitation is expired
     */
    public Set<Long> findPersonIdsWithExpiredInvitation(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return Set.of();
        }
        return pendingPersonRepository.findExpiredDisabledPersonIdsIn(personIds, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Tells whether the given invitation has passed its expiration date.
     *
     * @param pendingPerson the invitation to check
     * @return {@code true} when the invitation link is no longer valid
     */
    public boolean isExpired(PendingPerson pendingPerson) {
        return OffsetDateTime.now(ZoneOffset.UTC).isAfter(pendingPerson.getPendingInvitationExpirationDate());
    }

    /**
     * Renews the invitation of the given person, replacing any previous one: a fresh registration token
     * and a new expiration date are generated on the existing pending person (invalidating the old link),
     * or a new invitation is created if none existed yet.
     *
     * @param person the invited (still disabled) person whose invitation must be renewed
     * @return the refreshed pending person carrying the new token and expiration date
     */
    @Transactional
    public PendingPerson resendInvitation(PersonDTO person) {
        PendingPerson pendingPerson = pendingPersonRepository.findByDisabledPersonId(person.getId())
                .orElseGet(() -> {
                    PendingPerson created = new PendingPerson();
                    created.setDisabledPerson(personMapper.invertConvert(person));
                    return created;
                });
        pendingPerson.setRegisterToken(generateToken());
        pendingPerson.setPendingInvitationExpirationDate(OffsetDateTime.now(ZoneOffset.UTC).plusDays(INVITATION_VALIDITY_DAYS));
        return pendingPersonRepository.save(pendingPerson);
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

    @Transactional
    public void deleteByPerson(PersonDTO person) {
        pendingPersonRepository.deleteByDisabledPersonId(person.getId());
    }
}
