package fr.siamois.infrastructure.database.repositories.vocabulary.label;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ConceptLabelRepository extends CrudRepository<ConceptLabel, Long> {

    @Query(
            nativeQuery = true,
            value = "SELECT lacl.* FROM localized_alt_concept_label lacl " +
                    "WHERE lacl.fk_field_parent_concept_id = :fieldConceptId " +
                    "AND lacl.lang_code = :langCode " +
                    "AND unaccent(lacl.label) ILIKE unaccent('%' || :input || '%') " +
                    "LIMIT :limit"
    )
    List<ConceptLabel> findAllByParentConceptAndInputLimited(Long fieldConceptId, String langCode, String input, int limit);

    Set<ConceptPrefLabel> findAllPrefLabelsByConcept(Concept concept);

    Set<ConceptLabel> findAllLabelsByConcept(Concept concept);

    Optional<ConceptPrefLabel> findByConceptAndLangCode(Concept concept, String langCode);

    List<ConceptLabel> findAllLabelsByParentConcept(Concept parentConcept);

    Optional<ConceptAltLabel> findAltLabelByConceptAndLangCode(Concept savedConcept, String lang);

    Optional<ConceptPrefLabel> findPrefLabelByLangCodeAndConcept(String langCode, Concept concept);

    Set<ConceptAltLabel> findAllAltLabelsByLangCodeAndConcept(String langCode, Concept concept);

    List<ConceptLabel> findAllByParentConcept(Concept parentConcept);

    List<ConceptLabel> findAllLabelsByParentConceptAndLangCode(Concept parentConcept, String langCode, Limit limit);
}
