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

    /**
     * Batched counterpart of {@link #findAllPrefLabelsByConcept(Concept)}: fetches the pref labels
     * of every given concept in a single query, so a page of table rows can be label-primed in one
     * round trip instead of {@link fr.siamois.ui.bean.LabelBean#findLabelOf} querying once per
     * distinct concept it encounters.
     */
    Set<ConceptPrefLabel> findAllPrefLabelsByConceptIn(Collection<Concept> concepts);

    Optional<ConceptPrefLabel> findByConceptAndLangCode(Concept concept, String langCode);

    Optional<ConceptAltLabel> findAltLabelByConceptAndLangCode(Concept savedConcept, String lang);

    Optional<ConceptAltLabel> findAltLabelByConceptAndLangCodeAndLabel(Concept savedConcept, String lang, String label);

    Set<ConceptAltLabel> findAllAltLabelsByConcept(Concept concept);

    Optional<ConceptPrefLabel> findPrefLabelByLangCodeAndConcept(String langCode, Concept concept);

    Set<ConceptAltLabel> findAllAltLabelsByLangCodeAndConcept(String langCode, Concept concept);

    List<ConceptLabel> findAllByParentConcept(Concept parentConcept);

}
