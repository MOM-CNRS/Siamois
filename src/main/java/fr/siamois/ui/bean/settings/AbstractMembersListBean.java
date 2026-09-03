package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.dto.entity.PermissionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.email.InvitationMailer;
import fr.siamois.ui.email.InvitationMessages;
import jakarta.faces.event.ActionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private transient ProfileDTO selectedProfile;

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

    /** @return the profile currently shown in the read-only profile detail drawer, or {@code null} when closed. */
    public final ProfileDTO getSelectedProfile() {
        return selectedProfile;
    }

    /** @return {@code true} when the read-only profile detail drawer should be shown. */
    public final boolean isProfileDetailOpen() {
        return selectedProfile != null;
    }

    /**
     * Opens the read-only profile detail drawer for the profile stashed on the triggering component
     * (via {@code <f:attribute name="profile" .../>} — composite attribute method expressions can't take
     * arguments, so the profile travels on the component instead, same as {@code onProfileSelect}'s "member").
     */
    public final void openProfileDetail(ActionEvent event) {
        Object profile = event.getComponent().getAttributes().get("profile");
        if (profile instanceof ProfileDTO profileDTO) {
            this.selectedProfile = profileDTO;
        }
    }

    /** Closes the read-only profile detail drawer. */
    public final void closeProfileDetail() {
        this.selectedProfile = null;
    }

    private record PermissionThemeSpec(String themeKey, List<String> codes) {
    }

    public static final String PERMISSION_THEME_RECORDING = "permission.theme.recording";
    private static final List<PermissionThemeSpec> INSTANCE_PERMISSION_THEMES = List.of(
            new PermissionThemeSpec("permission.theme.instance", List.of(
                    PermissionConstants.INSTANCE_MANAGE_SETTINGS)),
            new PermissionThemeSpec("permission.theme.organisations", List.of(
                    PermissionConstants.ORGANIZATION_CREATE,
                    PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_SETTINGS,
                    PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS,
                    PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_PLACES,
                    PermissionConstants.INSTANCE_ACCESS_ORGANIZATIONS)),
            new PermissionThemeSpec(PERMISSION_THEME_RECORDING, List.of(
                    PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS,
                    PermissionConstants.INSTANCE_EDIT_PHASES,
                    PermissionConstants.INSTANCE_EDIT_FINDS,
                    PermissionConstants.INSTANCE_EDIT_CONTAINERS))
    );

    private static final List<PermissionThemeSpec> ORGANISATION_PERMISSION_THEMES = List.of(
            new PermissionThemeSpec("permission.theme.organisation", List.of(
                    PermissionConstants.ORGANIZATION_MANAGE_SETTINGS,
                    PermissionConstants.ORGANIZATION_ACCESS)),
            new PermissionThemeSpec("permission.theme.projects", List.of(
                    PermissionConstants.ORGANIZATION_MANAGE_ACTIONS,
                    PermissionConstants.ORGANIZATION_CREATE_ACTIONS)),
            new PermissionThemeSpec("permission.theme.spatialUnits", List.of(
                    PermissionConstants.ORGANIZATION_MANAGE_PLACES)),
            new PermissionThemeSpec(PERMISSION_THEME_RECORDING, List.of(
                    PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS,
                    PermissionConstants.ORGANIZATION_EDIT_PHASES,
                    PermissionConstants.ORGANIZATION_EDIT_FINDS,
                    PermissionConstants.ORGANIZATION_EDIT_CONTAINERS))
    );

    private static final List<PermissionThemeSpec> PROJECT_PERMISSION_THEMES = List.of(
            new PermissionThemeSpec("permission.theme.project", List.of(
                    PermissionConstants.PROJECT_MANAGE_SETTINGS)),
            new PermissionThemeSpec(PERMISSION_THEME_RECORDING, List.of(
                    PermissionConstants.PROJECT_EDIT_RECORDING_UNITS,
                    PermissionConstants.PROJECT_EDIT_PHASES,
                    PermissionConstants.PROJECT_EDIT_FINDS,
                    PermissionConstants.PROJECT_EDIT_CONTAINERS))
    );

    /** One row of the read-only permission checkbox list: a permission's label and whether the profile grants it. */
    @Getter
    public static final class PermissionRowView {
        private final String label;
        private final boolean granted;

        private PermissionRowView(String label, boolean granted) {
            this.label = label;
            this.granted = granted;
        }
    }

    /** One theme section of the read-only permission checkbox list, grouping related permission rows. */
    @Getter
    public static final class PermissionThemeView {
        private final String label;
        private final List<PermissionRowView> rows;

        private PermissionThemeView(String label, List<PermissionRowView> rows) {
            this.label = label;
            this.rows = rows;
        }
    }

    /**
     * @param profile the profile whose permissions should be described
     * @return the permissions of the profile's own scope, grouped by theme, each as a read-only checkbox
     *         row (checked when the profile grants it — all profiles are system/read-only for now). Every
     *         {@link PermissionConstants} code belongs to exactly one scope and a profile only ever holds
     *         codes of its own scope (see {@code ProfileService}), so this never needs to look outside it.
     */
    public final List<PermissionThemeView> permissionCatalogOf(ProfileDTO profile) {
        if (profile == null || profile.getScope() == null) {
            return List.of();
        }
        Set<String> granted = profile.getPermissions() == null
                ? Set.of()
                : profile.getPermissions().stream().map(PermissionDTO::getCode).collect(Collectors.toSet());
        List<PermissionThemeSpec> themes = switch (profile.getScope()) {
            case INSTANCE -> INSTANCE_PERMISSION_THEMES;
            case ORGANISATION -> ORGANISATION_PERMISSION_THEMES;
            case PROJECT -> PROJECT_PERMISSION_THEMES;
        };
        return themes.stream()
                .map(theme -> new PermissionThemeView(
                        langBean.msg(theme.themeKey()),
                        theme.codes().stream()
                                .map(code -> new PermissionRowView(langBean.msg("permission." + code), granted.contains(code)))
                                .toList()))
                .toList();
    }

    /** Clears the profile detail drawer state. Call from {@code reset()}. */
    protected final void resetProfileDetail() {
        selectedProfile = null;
    }

    /** @return the localised scope phrase (application / institution / project) shown in the invitation e-mail. */
    protected abstract String invitationScopeName();

    /** @return the localised subject of the invitation e-mail for this scope. */
    protected abstract String invitationMailSubject();

}
