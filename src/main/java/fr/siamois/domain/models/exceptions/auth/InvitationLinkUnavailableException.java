package fr.siamois.domain.models.exceptions.auth;

/** The invitation link is unknown, revoked, expired or its accounts quota is reached. */
public class InvitationLinkUnavailableException extends Exception {
    public InvitationLinkUnavailableException(String message) {
        super(message);
    }
}
