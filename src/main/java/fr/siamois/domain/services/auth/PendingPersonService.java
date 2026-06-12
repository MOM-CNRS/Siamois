package fr.siamois.domain.services.auth;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.pending.PendingActionUnitAttribution;
import fr.siamois.domain.models.auth.pending.PendingInstitutionInvite;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.LangService;
import fr.siamois.infrastructure.database.repositories.person.PendingActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.person.PendingInstitutionInviteRepository;
import fr.siamois.infrastructure.database.repositories.person.PendingPersonRepository;
import fr.siamois.ui.email.EmailManager;
import fr.siamois.utils.DateUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Service for managing pending persons. Pending persons are users who have been invited to register but have not yet completed the registration process.
 */
@Service
public class PendingPersonService {

    private final PendingPersonRepository pendingPersonRepository;
    private final SecureRandom random = new SecureRandom();

    public static final int MAX_GENERATION = 3000;
    public static final int TOKEN_LENGTH = 20;
    private static final String ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final HttpServletRequest httpServletRequest;
    private final PendingInstitutionInviteRepository pendingInstitutionInviteRepository;
    private final EmailManager emailManager;
    private final LangService langService;
    private final PendingActionUnitRepository pendingActionUnitRepository;

    public PendingPersonService(PendingPersonRepository pendingPersonRepository,
                                HttpServletRequest httpServletRequest,
                                PendingInstitutionInviteRepository pendingInstitutionInviteRepository,
                                EmailManager emailManager,
                                LangService langService, PendingActionUnitRepository pendingActionUnitRepository) {
        this.pendingPersonRepository = pendingPersonRepository;
        this.httpServletRequest = httpServletRequest;
        this.pendingInstitutionInviteRepository = pendingInstitutionInviteRepository;
        this.emailManager = emailManager;
        this.langService = langService;
        this.pendingActionUnitRepository = pendingActionUnitRepository;
    }

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
            return pendingPerson.get();
        } else {
            PendingPerson person = new PendingPerson();
            person.setEmail(email);
            person.setId(-1L);
            person.setRegisterToken(generateToken());
            person.setPendingInvitationExpirationDate(OffsetDateTime.now(ZoneOffset.UTC).plusDays(3));
            person = pendingPersonRepository.save(person);

            return person;
        }
    }

    /**
     * Send an invitation email to the pending person with the option to set them as a manager.
     *
     * @param pendingPerson the pending person
     * @param institution   the institution
     * @param isManager     true if the pending person should be a manager, false otherwise
     * @param mailLang      the language of the email
     * @return true if the email was sent, false if the invitation already exists
     */
    public boolean sendPendingManagerInstitutionInvite(PendingPerson pendingPerson, Institution institution, boolean isManager, String mailLang) {
        Optional<PendingInstitutionInvite> pendingInstitutionInvite = pendingInstitutionInviteRepository.findByInstitutionAndPendingPerson(institution, pendingPerson);
        if (pendingInstitutionInvite.isPresent()) {
            PendingInstitutionInvite invite = pendingInstitutionInvite.get();
            invite.setManager(isManager);
            pendingInstitutionInviteRepository.save(invite);
            return false;
        }
        sendEmail(pendingPerson, institution, mailLang, isManager, false);
        return true;
    }

    /**
     * Send an action manager invitation email to the pending person.
     *
     * @param pendingPerson the pending person
     * @param institution   the institution
     * @param mailLang      the language of the email
     * @return true if the email was sent, false if the invitation already exists
     */
    public boolean sendPendingActionManagerInstitutionInvite(PendingPerson pendingPerson, Institution institution, String mailLang) {
        Optional<PendingInstitutionInvite> optInvite = pendingInstitutionInviteRepository.findByInstitutionAndPendingPerson(institution, pendingPerson);
        if (optInvite.isPresent()) {
            return false;
        }
        sendEmail(pendingPerson, institution, mailLang, false, true);
        return true;
    }

    /**
     * Add a pending person to an action unit and send an invitation email if they are not already invited in the institution.
     *
     * @param pendingPerson the pending person to invite
     * @param actionUnit    the action unit to which the pending person is being invited
     * @param role          the role of the pending person in the action unit
     * @param mailLang      the language of the email to be sent
     * @return true if the email was sent, false if the pending person is already invited in the institution
     */
    public boolean sendPendingActionMemberInvite(PendingPerson pendingPerson, ActionUnit actionUnit, Concept role, String mailLang) {
        Optional<PendingInstitutionInvite> optInvite = pendingInstitutionInviteRepository.findByInstitutionAndPendingPerson(actionUnit.getCreatedByInstitution(), pendingPerson);
        if (optInvite.isPresent()) {
            PendingInstitutionInvite invite = optInvite.get();
            createIfNotExistAttribution(actionUnit, role, invite);
            return false;
        }

        PendingInstitutionInvite invite = new PendingInstitutionInvite();
        invite.setPendingPerson(pendingPerson);
        invite.setInstitution(actionUnit.getCreatedByInstitution());
        invite.setManager(false);

        invite = pendingInstitutionInviteRepository.save(invite);
        createIfNotExistAttribution(actionUnit, role, invite);
        sendEmail(pendingPerson, actionUnit.getCreatedByInstitution(), mailLang, false, false);
        return true;
    }

    private void createIfNotExistAttribution(ActionUnit actionUnit, Concept role, PendingInstitutionInvite invite) {
        Optional<PendingActionUnitAttribution> optAction = pendingActionUnitRepository.findByActionUnitAndInstitutionInvite(actionUnit, invite);
        if (optAction.isPresent())
            return;
        PendingActionUnitAttribution actionAttribution = new PendingActionUnitAttribution(invite, actionUnit);
        actionAttribution.setRole(role);

        pendingActionUnitRepository.save(actionAttribution);
    }

    private void sendEmail(PendingPerson pendingPerson, Institution institution, String mailLang, boolean isManager, boolean isActionManager) {
        PendingInstitutionInvite invite = new PendingInstitutionInvite();
        invite.setPendingPerson(pendingPerson);
        invite.setInstitution(institution);
        invite.setId(-1L);
        invite.setManager(isManager);
        invite.setActionManager(isActionManager);
        pendingInstitutionInviteRepository.save(invite);

        Locale locale = new Locale(mailLang);
        String institutionName = institution.getName();
        String invitationLink = invitationLink(pendingPerson);
        String expirationDate = DateUtils.formatOffsetDateTime(pendingPerson.getPendingInvitationExpirationDate());

        emailManager.sendEmail(pendingPerson.getEmail(),
                langService.msg("mail.invitation.subject", locale, institutionName),
                langService.msg("mail.invitation.body", locale, institutionName, invitationLink, expirationDate, expirationDate)
        );
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
     * Delete a pending action unit attribution from the database.
     *
     * @param pendingActionUnitAttribution the pending action unit attribution to delete
     */
    public void delete(PendingActionUnitAttribution pendingActionUnitAttribution) {
        pendingActionUnitRepository.delete(pendingActionUnitAttribution);
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

    /**
     * Find all pending action unit attributions for a given pending institution invite.
     *
     * @param invite the pending institution invite for which to find action attributions
     * @return a Set of PendingActionUnitAttribution associated with the pending institution invite
     */
    public Set<PendingActionUnitAttribution> findActionAttributionsByPendingInvite(PendingInstitutionInvite invite) {
        return pendingActionUnitRepository.findByInstitutionInvite(invite);
    }
}
