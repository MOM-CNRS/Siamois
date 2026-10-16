package fr.siamois.domain.services.relation;

import lombok.Getter;

import java.util.List;

/** A refused write on a relation link. {@link Reason} is what the REST layer maps to a status code. */
@Getter
public class RelationLinkException extends RuntimeException {

    public enum Reason {
        DEFINITION_NOT_FOUND,
        LINK_NOT_FOUND,
        /** An end does not exist, or is out of the caller's reach (the two are not told apart). */
        ENDPOINT_NOT_FOUND,
        KIND_NOT_ALLOWED,
        TYPE_NOT_ALLOWED,
        TYPE_MISMATCH,
        CROSS_PROJECT,
        MIRROR_READ_ONLY,
        FORBIDDEN,
        DUPLICATE,
        RULE_VIOLATION,
        INVALID_ATTRIBUTE
    }

    private final Reason reason;
    private final List<RuleViolation> violations;

    public RelationLinkException(Reason reason, String message) {
        this(reason, message, List.of());
    }

    public RelationLinkException(Reason reason, String message, List<RuleViolation> violations) {
        super(message);
        this.reason = reason;
        this.violations = violations;
    }
}
