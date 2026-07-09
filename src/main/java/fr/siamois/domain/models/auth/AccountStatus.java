package fr.siamois.domain.models.auth;

import lombok.Getter;

/**
 * Display status of a user account in the member lists: an enabled account is active,
 * a disabled account with a pending registration invitation shows the invitation state.
 */
@Getter
public enum AccountStatus {

    ACTIVE("common.label.accountStatus.enabled", "chip-status-active"),
    DISABLED("common.label.accountStatus.disabled", "chip-status-inactive"),
    INVITATION_PENDING("common.label.accountStatus.invitationPending", "chip-status-pending"),
    INVITATION_EXPIRED("common.label.accountStatus.invitationExpired", "chip-status-expired");

    private final String labelKey;
    private final String styleClass;

    AccountStatus(String labelKey, String styleClass) {
        this.labelKey = labelKey;
        this.styleClass = styleClass;
    }
}
