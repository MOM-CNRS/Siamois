package fr.siamois.domain.models.form.rules;

import java.util.Optional;

/**
 * Résout l'id interne d'un concept à partir de ses identifiants externes, pour la sérialisation
 * des règles vers le front (voir {@link FieldRulesJson#toWire}).
 */
@FunctionalInterface
public interface ConceptIdLookup {

    /** Aucun concept résolu : la valeur part sans {@code conceptId} et ne matche rien côté front. */
    ConceptIdLookup NONE = (vocabularyExtId, conceptExtId) -> Optional.empty();

    Optional<Long> conceptId(String vocabularyExtId, String conceptExtId);
}
