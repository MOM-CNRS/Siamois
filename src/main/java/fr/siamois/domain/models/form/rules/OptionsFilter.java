package fr.siamois.domain.models.form.rules;

import java.io.Serializable;
import java.util.Set;

/**
 * Restriction de la liste des valeurs proposées d'un champ, selon la valeur d'un autre champ ou
 * selon le type de la fiche ({@link RelatedToType}). La configuration propre à un type
 * ({@code valueConceptId} : une source de vocabulaire réglée pour ce type) n'en fait pas partie :
 * elle est implicite et toujours appliquée.
 */
public sealed interface OptionsFilter extends Serializable
        permits OptionsFilter.RelatedConcepts, OptionsFilter.RelatedToType, OptionsFilter.RefMatch {

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
     * Concepts <em>liés</em> (relation thésaurus) au <em>type de la fiche</em> : la liste proposée est celle des
     * valeurs que le thésaurus relie au type (US, Fait, Métal…). Aucun champ source ; le type vient de l'entité.
     * Un type que le thésaurus ne relie à aucune valeur de ce champ laisse la liste complète.
     */
    record RelatedToType() implements OptionsFilter {
        @Override
        public Set<Long> fieldIds() {
            return Set.of();
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
