package fr.siamois.ui.email;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Renders and sends the invitation e-mail. Centralises the delivery plumbing (inviter resolution,
 * registration link, expiration date, HTML rendering and transport) shared by the "add member"
 * wizard dialogs and the members-list "resend invitation" action.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvitationMailer {

    private final EmailManager emailManager;
    private final InvitationEmailRenderer invitationEmailRenderer;
    private final PendingPersonService pendingPersonService;
    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;

    /**
     * Renders the invitation e-mail body for the given pending invitation and sends it to the invitee.
     * The body states who is inviting, on which scope and with which profiles, plus the registration
     * link and its expiration date.
     *
     * @param pendingPerson the invitation carrying the registration token and expiration date
     * @param invitee       the person being invited (recipient of the e-mail)
     * @param scopeName     the localised scope phrase (application / institution / project)
     * @param subject       the localised e-mail subject
     * @param profilesLabel the human-readable list of profiles granted to the invitee
     * @return {@code true} when the e-mail was handed to the transport, {@code false} when delivery failed
     */
    public boolean send(PendingPerson pendingPerson, PersonDTO invitee, String scopeName, String subject, String profilesLabel) {
        String invitationLink = pendingPersonService.invitationLink(pendingPerson);
        String expirationDate = DateUtils.formatOffsetDateTime(pendingPerson.getPendingInvitationExpirationDate());
        String body = invitationEmailRenderer.render(inviterName(), scopeName, profilesLabel, invitationLink, expirationDate);
        try {
            emailManager.sendEmail(invitee.getEmail(), subject, body, EmailManager.TEXT_HTML);
            return true;
        } catch (RuntimeException e) {
            log.error("Failed to send invitation e-mail to {}", invitee.getEmail(), e);
            return false;
        }
    }

    /**
     * Resolves the display name of the currently authenticated user, shown as the inviter in the e-mail.
     *
     * @return the inviter's display name, their username as a fallback, or a generic label when no user
     * is authenticated
     */
    public String inviterName() {
        PersonDTO inviter = sessionSettingsBean.getAuthenticatedUser();
        if (inviter == null) {
            return langBean.msg("mail.invitation.inviter.unknown");
        }
        String displayName = inviter.displayName();
        return StringUtils.isBlank(displayName) ? inviter.getUsername() : displayName.trim();
    }
}
