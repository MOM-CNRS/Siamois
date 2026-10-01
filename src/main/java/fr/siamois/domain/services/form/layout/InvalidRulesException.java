package fr.siamois.domain.services.form.layout;

import fr.siamois.domain.services.form.rules.FieldRulesValidator;

import java.util.List;

/** The rules a user tried to store are not valid; {@link #getIssues()} says why. */
public class InvalidRulesException extends RuntimeException {

    private final transient List<FieldRulesValidator.Issue> issues;

    public InvalidRulesException(List<FieldRulesValidator.Issue> issues) {
        super("Invalid field rules: " + issues);
        this.issues = issues;
    }

    public List<FieldRulesValidator.Issue> getIssues() {
        return issues;
    }
}
