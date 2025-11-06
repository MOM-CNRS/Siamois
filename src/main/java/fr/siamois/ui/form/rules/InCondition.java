package fr.siamois.ui.form.rules;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customform.ValueMatcher;

import java.util.List;
import java.util.Set;

public class InCondition implements Condition {
    private final CustomField field;
    private final List<ValueMatcher> matchers;
    public InCondition(CustomField field, List<ValueMatcher> matchers) { this.field = field; this.matchers = matchers; }
    public boolean test(ValueProvider vp) {
        CustomFieldAnswer cur = vp.getCurrentAnswer(field);
        if (cur == null) return false;
        for (ValueMatcher m : matchers) if (m.matches(cur)) return true;
        return false;
    }
    public Set<CustomField> dependsOn() { return java.util.Set.of(field); }
}

