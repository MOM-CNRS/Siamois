package fr.siamois.domain.models;

/**
 * Workflow status of a {@link TraceableEntity}: "en cours", "terminé", "validé", "annulé".
 * Stored as its name (EnumType.STRING); the database CHECK constraints list these exact values
 * (changelog 2026.09.24-0).
 */
public enum ValidationStatus {
    INCOMPLETE,
    COMPLETE,
    VALIDATED,
    CANCELLED;

    /**
     * The next status of the legacy JSF one-click cycle (en cours → terminé → validé → en cours).
     * An "annulé" entity re-enters the cycle at "en cours".
     */
    public ValidationStatus nextInCycle() {
        return switch (this) {
            case INCOMPLETE -> COMPLETE;
            case COMPLETE -> VALIDATED;
            case VALIDATED, CANCELLED -> INCOMPLETE;
        };
    }

    /**
     * Whether moving from this status to {@code target} needs the validator right on top of the
     * edit right: reaching "validé", or leaving it (an editor must not undo a validation).
     */
    public boolean requiresValidatorTo(ValidationStatus target) {
        return this != target && (target == VALIDATED || this == VALIDATED);
    }
}
