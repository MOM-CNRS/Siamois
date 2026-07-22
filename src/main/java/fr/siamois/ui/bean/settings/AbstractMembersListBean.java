package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.email.InvitationMailer;
import fr.siamois.ui.email.InvitationMessages;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;
import static fr.siamois.utils.MessageUtils.displayInfoMessage;

/**
 * Base class for the members-list settings beans (institution, project, application-wide).
 * Tracks which listed members still have a pending invitation (and whether it has expired) so the
 * datatable can display an "invitation sent" / "invitation expired" account status chip, and offers
 * the shared "resend invitation" action that replaces an expired invitation with a fresh one.
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractMembersListBean implements SettingsDatatableBean {

    protected final transient PendingPersonService pendingPersonService;
    protected final transient InvitationMailer invitationMailer;
    protected final LangBean langBean;

    private transient Set<Long> pendingInvitationPersonIds;
    private transient Set<Long> expiredInvitationPersonIds;

    /** Loads the pending- and expired-invitation state for the given listed members. Call from {@code init(...)}. */
    protected final void loadPendingInvitations(Collection<Long> memberPersonIds) {
        pendingInvitationPersonIds = new HashSet<>(pendingPersonService.findPersonIdsWithPendingInvitation(memberPersonIds));
        expiredInvitationPersonIds = new HashSet<>(pendingPersonService.findPersonIdsWithExpiredInvitation(memberPersonIds));
    }

    /** Tracks a member added after the initial load. Call when a new member joins the list. */
    protected final void trackPendingInvitation(PersonDTO person) {
        if (!person.isEnabled() && pendingPersonService.hasPendingInvitation(person.getId())) {
            pendingInvitationPersonIds.add(person.getId());
        }
    }

    /** Clears the pending-invitation state. Call from {@code reset()}. */
    protected final void resetPendingInvitations() {
        pendingInvitationPersonIds = null;
        expiredInvitationPersonIds = null;
    }

    /**
     * @param person the listed member
     * @return {@code true} when the member's account is disabled and still waiting on its invitation
     */
    public final boolean hasPendingInvitation(PersonDTO person) {
        return !person.isEnabled()
                && pendingInvitationPersonIds != null
                && pendingInvitationPersonIds.contains(person.getId());
    }

    /**
     * @param person the listed member
     * @return {@code true} when the member's account is disabled and its invitation has expired
     */
    public final boolean isInvitationExpired(PersonDTO person) {
        return !person.isEnabled()
                && expiredInvitationPersonIds != null
                && expiredInvitationPersonIds.contains(person.getId());
    }

    /**
     * @param person the listed member
     * @return the CSS chip class matching the member's account status (active / expired / invited / disabled)
     */
    public final String accountStatusChipClass(PersonDTO person) {
        if (person.isEnabled()) {
            return "chip-status-active";
        }
        if (isInvitationExpired(person)) {
            return "chip-status-expired";
        }
        if (hasPendingInvitation(person)) {
            return "chip-status-invited";
        }
        return "chip-status-inactive";
    }

    /**
     * @param person the listed member
     * @return the localised label matching the member's account status (active / expired / invited / disabled)
     */
    public final String accountStatusLabel(PersonDTO person) {
        if (person.isEnabled()) {
            return langBean.msg("common.label.accountStatus.enabled");
        }
        if (isInvitationExpired(person)) {
            return langBean.msg("common.label.accountStatus.expired");
        }
        if (hasPendingInvitation(person)) {
            return langBean.msg("common.label.accountStatus.invited");
        }
        return langBean.msg("common.label.accountStatus.disabled");
    }

    /**
     * Renews the invitation of the given member — replacing the previous (expired) link with a fresh one —
     * and re-sends the invitation e-mail, then updates the tracked status so the chip flips back to
     * "invitation sent".
     *
     * @param invitee  the invited (still disabled) member whose invitation must be renewed
     * @param profiles the profiles the member currently holds in this scope, listed in the e-mail
     */
    protected final void resendInvitationTo(PersonDTO invitee, Collection<ProfileDTO> profiles) {
        PendingPerson pendingPerson = pendingPersonService.resendInvitation(invitee);
        boolean sent = invitationMailer.send(pendingPerson, invitee, invitationScopeName(),
                invitationMailSubject(), InvitationMessages.profilesLabel(langBean, profiles));
        if (expiredInvitationPersonIds != null) {
            expiredInvitationPersonIds.remove(invitee.getId());
        }
        if (pendingInvitationPersonIds != null) {
            pendingInvitationPersonIds.add(invitee.getId());
        }
        if (sent) {
            displayInfoMessage(langBean, "newMember.invitation.resent", invitee.getEmail());
        } else {
            displayErrorMessage(langBean, "newMember.invitation.failed", invitee.getEmail());
        }
    }

    /** @return the localised scope phrase (application / institution / project) shown in the invitation e-mail. */
    protected abstract String invitationScopeName();

    /** @return the localised subject of the invitation e-mail for this scope. */
    protected abstract String invitationMailSubject();

}
