package fr.siamois.ui.form.rules;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;

import java.util.Objects;
import java.util.Set;

/** enabled si answer(field) == target */
public final class EqualsAnswer implements Condition {
    private final CustomField field;
    private final CustomFieldAnswer target;

    public EqualsAnswer(CustomField field, CustomFieldAnswer target) {
        requireSameField(field, target);
        this.field = field;
        this.target = target;
    }

    @Override
    public boolean test(ValueProvider vp) {
        return Objects.equals(vp.getCurrentAnswer(field), target);
    }

    @Override
    public Set<CustomField> dependsOn() {
        return Set.of(field);
    }

    private static void requireSameField(CustomField field, CustomFieldAnswer target) {
        if (target == null || target.getPk() == null || target.getPk().getField() == null) {
            throw new IllegalArgumentException("target answer or its field is null");
        }
        if (!field.equals(target.getPk().getField())) {
            throw new IllegalArgumentException("Target answer belongs to another CustomField");
        }
    }
}
