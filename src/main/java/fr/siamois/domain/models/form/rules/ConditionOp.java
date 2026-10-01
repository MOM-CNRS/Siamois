package fr.siamois.domain.models.form.rules;

/** Opérateur d'une {@link Condition.Leaf}. EMPTY / NOT_EMPTY ne prennent pas de valeur. */
public enum ConditionOp {
    EQ, NEQ, IN, NOT_IN, EMPTY, NOT_EMPTY, GT, GTE, LT, LTE
}
