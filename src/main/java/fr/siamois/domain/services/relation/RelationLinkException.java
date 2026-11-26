package fr.siamois.domain.services.relation;

import lombok.Getter;

import java.util.List;

/**
 * A refused write on a relation: a link, a definition or a rule set. {@link Reason} is what the REST layer maps to
 * a status code; {@code violations} details a broken rule, with the links in cause when there are some.
 */
@Getter
public class RelationLinkException extends RuntimeException {

    public enum Reason {
        DEFINITION_NOT_FOUND,
        LINK_NOT_FOUND,
        /** An end does not exist, or is out of the caller's reach (the two are not told apart). */
        ENDPOINT_NOT_FOUND,
        KIND_NOT_ALLOWED,
        TYPE_NOT_ALLOWED,
        CROSS_PROJECT,
        /** A link from a record to itself. */
        SELF_LINK,
        FORBIDDEN,
        DUPLICATE,
        INVALID_QUALIFIER,
        RULE_VIOLATION,
        /** A definition or rule set change that its existing links, or its fields, do not allow. */
        INVALID_CONFIGURATION
    }

    private final Reason reason;
    private final transient List<RuleViolation> violations;

    public RelationLinkException(Reason reason, String message) {
        this(reason, message, List.of());
    }

    public RelationLinkException(Reason reason, String message, List<RuleViolation> violations) {
        super(message);
        this.reason = reason;
        this.violations = List.copyOf(violations);
    }
}
