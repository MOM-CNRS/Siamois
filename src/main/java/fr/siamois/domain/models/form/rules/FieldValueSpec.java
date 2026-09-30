package fr.siamois.domain.models.form.rules;

import java.io.Serializable;
import java.util.Objects;

/**
 * Valeur attendue par une {@link Condition.Leaf}, typée selon la famille du champ comparé.
 */
public sealed interface FieldValueSpec extends Serializable
        permits FieldValueSpec.ConceptValue, FieldValueSpec.RefValue, FieldValueSpec.LiteralValue {

    /**
     * Un concept, par son id interne SIAMOIS — celui que le client compare directement avec
     * {@code ResourceRef.resourceId}. Les fichiers de mise en page initiale désignent les concepts
     * par leurs identifiants externes ; ils sont convertis en id interne à la lecture.
     */
    record ConceptValue(long conceptId) implements FieldValueSpec {
    }

    /** Une entité référencée (personne, unité, lieu…), par son id d'API. */
    record RefValue(String id) implements FieldValueSpec {
        public RefValue {
            Objects.requireNonNull(id, "id");
        }
    }

    /** Un littéral : texte, nombre, booléen, ou date au format ISO-8601 (chaîne). */
    record LiteralValue(Object value) implements FieldValueSpec {
        public LiteralValue {
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw new IllegalArgumentException("Unsupported literal: " + value);
            }
        }
    }

    static FieldValueSpec concept(long conceptId) {
        return new ConceptValue(conceptId);
    }

    static FieldValueSpec ref(String id) {
        return new RefValue(id);
    }

    static FieldValueSpec literal(Object value) {
        return new LiteralValue(value);
    }
}
