package fr.siamois.domain.models.form.rules;

import java.io.Serializable;
import java.util.Objects;

/**
 * Valeur attendue par une {@link Condition.Leaf}, typée selon la famille du champ comparé.
 */
public sealed interface FieldValueSpec extends Serializable
        permits FieldValueSpec.ConceptValue, FieldValueSpec.RefValue, FieldValueSpec.LiteralValue {

    /**
     * Un concept, identifié par ses identifiants externes (thésaurus), portables d'une instance à
     * l'autre. L'id interne ({@code conceptId}) n'est pas stocké : il est résolu à la sérialisation
     * vers le front (voir {@link ConceptIdLookup}), pour que le client compare directement avec
     * {@code ResourceRef.resourceId}.
     */
    record ConceptValue(String vocabularyExtId, String conceptExtId) implements FieldValueSpec {
        public ConceptValue {
            Objects.requireNonNull(vocabularyExtId, "vocabularyExtId");
            Objects.requireNonNull(conceptExtId, "conceptExtId");
        }
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

    static FieldValueSpec concept(String vocabularyExtId, String conceptExtId) {
        return new ConceptValue(vocabularyExtId, conceptExtId);
    }

    static FieldValueSpec ref(String id) {
        return new RefValue(id);
    }

    static FieldValueSpec literal(Object value) {
        return new LiteralValue(value);
    }
}
