package fr.siamois.infrastructure.database.repositories.vocabulary.label;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ConceptLabelRepository extends CrudRepository<ConceptLabel, Long> {

    Set<ConceptPrefLabel> findAllPrefLabelsByConcept(Concept concept);

    Optional<ConceptPrefLabel> findByConceptAndLangCode(Concept concept, String langCode);

    Optional<ConceptAltLabel> findAltLabelByConceptAndLangCode(Concept savedConcept, String lang);

    Optional<ConceptAltLabel> findAltLabelByConceptAndLangCodeAndLabel(Concept savedConcept, String lang, String label);

    Set<ConceptAltLabel> findAllAltLabelsByConcept(Concept concept);

    Optional<ConceptPrefLabel> findPrefLabelByLangCodeAndConcept(String langCode, Concept concept);

    Set<ConceptAltLabel> findAllAltLabelsByLangCodeAndConcept(String langCode, Concept concept);

    List<ConceptLabel> findAllByParentConcept(Concept parentConcept);

    /**
     * Batched counterpart of {@link #findPrefLabelByLangCodeAndConcept(String, Concept)} — one query for a
     * whole page's worth of concepts instead of one (or two) per concept. Used by
     * {@code ConceptLabelBatchResolver}.
     */
    List<ConceptPrefLabel> findAllPrefLabelsByLangCodeAndConcept_IdIn(String langCode, Collection<Long> conceptIds);

    /**
     * Batched counterpart of {@link #findAllAltLabelsByLangCodeAndConcept(String, Concept)}.
     */
    List<ConceptAltLabel> findAllAltLabelsByLangCodeAndConcept_IdIn(String langCode, Collection<Long> conceptIds);

}
