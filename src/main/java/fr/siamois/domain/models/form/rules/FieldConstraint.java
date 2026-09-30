package fr.siamois.domain.models.form.rules;

import java.io.Serializable;
import java.util.Objects;

/**
 * Contrainte inter-champs : {@code valeur(ce champ) op valeur(fieldId)}, ex. fermeture GTE
 * ouverture. Déclarée d'un seul côté, vérifiée dans les deux sens par les évaluateurs.
 * Sans effet tant que l'un des deux champs est vide.
 */
public record FieldConstraint(Op op, long fieldId) implements Serializable {

    public enum Op {GT, GTE, LT, LTE}

    public FieldConstraint {
        Objects.requireNonNull(op, "op");
    }

    public static FieldConstraint gte(long fieldId) {
        return new FieldConstraint(Op.GTE, fieldId);
    }

    public static FieldConstraint lte(long fieldId) {
        return new FieldConstraint(Op.LTE, fieldId);
    }
}
