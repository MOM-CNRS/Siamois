package fr.siamois.domain.models.auth.invitation;

/** The state of an invitation link, computed from its fields and the number of accounts created through it. */
public enum InvitationLinkStatus {
    ACTIVE,
    REVOKED,
    EXPIRED,
    FULL
}
