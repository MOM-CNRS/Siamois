package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.relation.RelationRuleKind;
import fr.siamois.domain.models.relation.RelationRuleSeverity;

/** A relation rule a link would break (blocking: the write is refused; warning: it goes through and is reported). */
public record RuleViolation(RelationRuleKind rule, RelationRuleSeverity severity, String message) {
}
