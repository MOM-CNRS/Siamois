package fr.siamois.ui.form.rules;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customform.ValueMatcher;

import java.util.Set;

public class EqCondition implements Condition {
    private final CustomField field;
    private final ValueMatcher matcher;
    public EqCondition(CustomField field, ValueMatcher matcher) { this.field = field; this.matcher = matcher; }
    public boolean test(ValueProvider vp) {
        CustomFieldAnswer cur = vp.getCurrentAnswer(field);
        return matcher.matches(cur);
    }
    public Set<CustomField> dependsOn() { return java.util.Set.of(field); }
}