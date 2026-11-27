package fr.siamois.domain.services.relation;

import java.util.List;

/**
 * A rule a write would break, or that existing links break. A blocking one refuses the write; a warning (a cycle
 * through an uncertain link) lets it through and is reported with the stored link.
 *
 * @param linkIds the links in cause: the path of a cycle, or the existing links a restricted rule set would break
 */
public record RuleViolation(Rule rule, boolean blocking, String message, List<Long> linkIds) {

    public enum Rule {
        NO_CYCLE,
        SINGLE_CHOICE,
        EXCLUSION,
        ACCEPTED_KIND
    }

    public RuleViolation {
        linkIds = List.copyOf(linkIds);
    }
}
