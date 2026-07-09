package fr.siamois.domain.services.auth;

import fr.siamois.domain.models.auth.AccountStatus;
import fr.siamois.domain.models.auth.pending.PendingInstitutionInvite;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.services.LangService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.person.PendingInstitutionInviteRepository;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.ui.email.EmailManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Locale;
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
    private static final String INVITATION_TEMPLATE_PATH = "email-templates/invitation.html";

    private final HttpServletRequest httpServletRequest;
    private final PendingInstitutionInviteRepository pendingInstitutionInviteRepository;
    private final EmailManager emailManager;
    private final LangService langService;

    private String invitationTemplate;

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
     * Create or get a pending person by email.
     *
     * @param email the email of the pending person
     * @return the pending person
     */
    public PendingPerson createOrGetPendingPerson(@Email String email) {
        Optional<PendingPerson> pendingPerson = pendingPersonRepository.findByEmail(email);
        if (pendingPerson.isPresent()) {
            PendingPerson person = pendingPerson.get();
            if (invitationIsExpired(person)) {
                person.setRegisterToken(generateToken());
                person.setPendingInvitationExpirationDate(OffsetDateTime.now(ZoneOffset.UTC).plusDays(INVITATION_VALIDITY_DAYS));
                person = pendingPersonRepository.save(person);
            }
            return person;
        } else {
            PendingPerson person = new PendingPerson();
            person.setEmail(email);
            person.setId(-1L);
            person.setRegisterToken(generateToken());
            person.setPendingInvitationExpirationDate(OffsetDateTime.now(ZoneOffset.UTC).plusDays(INVITATION_VALIDITY_DAYS));
            person = pendingPersonRepository.save(person);

            return person;
        }
    }

    /**
     * Check whether the registration invitation of a pending person is expired.
     *
     * @param pendingPerson the pending person
     * @return true if the invitation expiration date is in the past
     */
    public boolean invitationIsExpired(PendingPerson pendingPerson) {
        return pendingPerson.getPendingInvitationExpirationDate() == null
                || OffsetDateTime.now(ZoneOffset.UTC).isAfter(pendingPerson.getPendingInvitationExpirationDate());
    }

    /**
     * Display status of an account in the member lists: an enabled account is active; a disabled
     * account with a registration invitation shows the invitation state (pending or expired).
     *
     * @param person the person whose account status to compute
     * @return the account status
     */
    public AccountStatus accountStatusOf(PersonDTO person) {
        if (person.isEnabled()) {
            return AccountStatus.ACTIVE;
        }
        Optional<PendingPerson> pending = pendingPersonRepository.findByEmail(person.getEmail());
        if (pending.isEmpty()) {
            return AccountStatus.DISABLED;
        }
        return invitationIsExpired(pending.get()) ? AccountStatus.INVITATION_EXPIRED : AccountStatus.INVITATION_PENDING;
    }

    /**
     * Invite a person to create their account: create (or refresh) the pending person carrying the
     * registration token and send them the invitation email built from the HTML template.
     *
     * @param email       the email address to invite
     * @param contextName the name displayed in the email (organization, project or application name)
     * @param mailLang    the language of the email subject
     * @return the pending person carrying the registration token
     */
    public PendingPerson invitePersonByEmail(@Email String email, String contextName, String mailLang) {
        PendingPerson pendingPerson = createOrGetPendingPerson(email);
        sendInvitationEmail(pendingPerson, contextName, mailLang);
        return pendingPerson;
    }

    /**
     * Invite a pending person to an institution with the given profiles. The profiles are stored
     * on the invite and assigned to the person when they complete their registration.
     * <p>
     * If an invite already exists for this person and institution, the profiles are merged into it
     * and no new email is sent. Otherwise the invite is created and the invitation email is sent.
     *
     * @param pendingPerson the pending person to invite
     * @param institution   the institution the person is invited to
     * @param profiles      the profiles to assign upon registration; PROJECT-scoped profiles carry their own action unit
     * @param mailLang      the language of the invitation email
     * @return true if the email was sent, false if the invitation already existed
     */
    public boolean sendInvite(PendingPerson pendingPerson, Institution institution, Collection<Profile> profiles, String mailLang) {
        Optional<PendingInstitutionInvite> existingInvite = pendingInstitutionInviteRepository.findByInstitutionAndPendingPerson(institution, pendingPerson);
        if (existingInvite.isPresent()) {
            PendingInstitutionInvite invite = existingInvite.get();
            invite.getProfiles().addAll(profiles);
            pendingInstitutionInviteRepository.save(invite);
            return false;
        }

        PendingInstitutionInvite invite = new PendingInstitutionInvite();
        invite.setPendingPerson(pendingPerson);
        invite.setInstitution(institution);
        invite.getProfiles().addAll(profiles);
        pendingInstitutionInviteRepository.save(invite);

        sendInvitationEmail(pendingPerson, institution.getName(), mailLang);
        return true;
    }

    private void sendInvitationEmail(PendingPerson pendingPerson, String contextName, String mailLang) {
        Locale locale = new Locale(mailLang);
        String invitationLink = invitationLink(pendingPerson);
        String validity = langService.msg("mail.invitation.validity", locale, remainingValidityDays(pendingPerson));

        String htmlBody = String.format(invitationTemplate(), contextName, invitationLink, invitationLink, validity);

        emailManager.sendHtmlEmail(pendingPerson.getEmail(),
                langService.msg("mail.invitation.subject", locale, contextName),
                htmlBody
        );
    }

    private long remainingValidityDays(PendingPerson pendingPerson) {
        Duration remaining = Duration.between(OffsetDateTime.now(ZoneOffset.UTC), pendingPerson.getPendingInvitationExpirationDate());
        return Math.max(1, (long) Math.ceil(remaining.toMinutes() / (60.0 * 24)));
    }

    private String invitationTemplate() {
        if (invitationTemplate == null) {
            try (InputStream stream = new ClassPathResource(INVITATION_TEMPLATE_PATH).getInputStream()) {
                invitationTemplate = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load invitation email template " + INVITATION_TEMPLATE_PATH, e);
            }
        }
        return invitationTemplate;
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
     * Delete a pending institution invite from the database.
     *
     * @param pendingInstitutionInvite the pending institution invite to delete
     */
    public void delete(PendingInstitutionInvite pendingInstitutionInvite) {
        pendingInstitutionInviteRepository.delete(pendingInstitutionInvite);
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

    /**
     * Find all pending institution invites for a given pending person.
     *
     * @param pendingPerson the pending person for whom to find institution invites
     * @return a Set of PendingInstitutionInvite associated with the pending person
     */
    public Set<PendingInstitutionInvite> findInstitutionsByPendingPerson(PendingPerson pendingPerson) {
        return pendingInstitutionInviteRepository.findAllByPendingPerson(pendingPerson);
    }
}
