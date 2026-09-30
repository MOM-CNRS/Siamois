package fr.siamois.domain.models.form.rules;

import java.io.Serializable;
import java.util.Set;

/**
 * Restriction de la liste des valeurs proposées d'un champ, selon la valeur d'un autre champ.
 * La restriction par type de l'entité ({@code valueConceptId}) n'en fait pas partie : elle est
 * implicite et toujours appliquée.
 */
public sealed interface OptionsFilter extends Serializable
        permits OptionsFilter.RelatedConcepts, OptionsFilter.RefMatch {

    Set<Long> fieldIds();

    /**
     * Concepts <em>liés</em> (relation thésaurus) au concept choisi dans {@code fieldId}. Remplace
     * l'ancien {@code dependsOn}.
     */
    record RelatedConcepts(long fieldId) implements OptionsFilter {
        @Override
        public Set<Long> fieldIds() {
            return Set.of(fieldId);
        }
    }

    /**
     * Pour un champ référence : seules les entités candidates dont le champ {@code candidateFieldId}
     * vaut la valeur du champ {@code fieldId} de la fiche (ex. parents d'une UE limités au même lieu).
     */
    record RefMatch(long fieldId, long candidateFieldId) implements OptionsFilter {
        @Override
        public Set<Long> fieldIds() {
            return Set.of(fieldId);
        }
    }
}
