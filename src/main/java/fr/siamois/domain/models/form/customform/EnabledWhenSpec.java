package fr.siamois.domain.models.form.customform;

package fr.siamois.domain.models.form.customform;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Représente une condition d'activation pour une colonne.
 * Exemple :
 *   enabledWhen = (agent geomorphologique == "erosion")
 *   ou           (agent geomorphologique  in {"depot","interface"})
 */
public class EnabledWhenSpec implements Serializable {

    public enum Operator { EQUALS, NOT_EQUALS, IN }

    /** opérateur logique */
    private Operator operator;

    /** Valeurs attendues : une ou plusieurs CustomFieldAnswer du même CustomField */
    private List<CustomFieldAnswer> expectedValues = new ArrayList<>();

    public Operator getOperator() { return operator; }
    public void setOperator(Operator operator) { this.operator = operator; }

    public List<CustomFieldAnswer> getExpectedValues() { return expectedValues; }
    public void setExpectedValues(List<CustomFieldAnswer> expectedValues) { this.expectedValues = expectedValues; }

    /** Champ comparé, inféré depuis la première valeur */
    public CustomField getComparedField() {
        if (expectedValues == null || expectedValues.isEmpty()) return null;
        var pk = expectedValues.get(0).getPk();
        return pk != null ? pk.getField() : null;
    }

    /** validation de cohérence */
    public void validate() {
        Objects.requireNonNull(operator, "operator is required");
        if ((operator == Operator.EQUALS || operator == Operator.NOT_EQUALS)) {
            if (expectedValues == null || expectedValues.size() != 1) {
                throw new IllegalArgumentException("EQUALS/NOT_EQUALS require exactly 1 expected value");
            }
        }
        if (operator == Operator.IN && (expectedValues == null || expectedValues.isEmpty())) {
            throw new IllegalArgumentException("IN requires at least 1 expected value");
        }

        // vérifie que toutes les expectedValues appartiennent au même CustomField
        CustomField ref = getComparedField();
        if (ref == null) throw new IllegalArgumentException("expectedValues must reference a field");
        for (CustomFieldAnswer v : expectedValues) {
            if (v == null || v.getPk() == null || v.getPk().getField() == null)
                throw new IllegalArgumentException("A value or its field is null");
            if (!ref.equals(v.getPk().getField()))
                throw new IllegalArgumentException("All expectedValues must belong to the same CustomField");
        }
    }
}
