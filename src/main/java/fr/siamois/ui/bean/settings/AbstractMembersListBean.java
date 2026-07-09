package fr.siamois.ui.bean.settings;

import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.dto.entity.PersonDTO;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Base class for the members-list settings beans (institution, project, application-wide).
 * Tracks which listed members still have a pending invitation so the datatable can display
 * an "invitation sent" account status chip.
 */
public abstract class AbstractMembersListBean implements SettingsDatatableBean {

    protected final transient PendingPersonService pendingPersonService;

    private transient Set<Long> pendingInvitationPersonIds;

    protected AbstractMembersListBean(PendingPersonService pendingPersonService) {
        this.pendingPersonService = pendingPersonService;
    }

    /** Loads the pending-invitation state for the given listed members. Call from {@code init(...)}. */
    protected final void loadPendingInvitations(Collection<Long> memberPersonIds) {
        pendingInvitationPersonIds = new HashSet<>(pendingPersonService.findPersonIdsWithPendingInvitation(memberPersonIds));
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
    }

    /** @return true when the member's account is disabled and still waiting on its invitation. */
    public final boolean hasPendingInvitation(PersonDTO person) {
        return !person.isEnabled()
                && pendingInvitationPersonIds != null
                && pendingInvitationPersonIds.contains(person.getId());
    }

}
