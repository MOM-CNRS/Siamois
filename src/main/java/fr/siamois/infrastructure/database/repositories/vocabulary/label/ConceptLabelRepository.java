package fr.siamois.infrastructure.database.repositories.vocabulary.label;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
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
            value = "SELECT lacl.* FROM concept_label lacl " +
                    "JOIN concept c ON lacl.fk_concept_id = c.concept_id " +
                    "WHERE lacl.fk_field_parent_concept_id = :fieldConceptId " +
                    "AND lacl.lang_code = :langCode " +
                    "AND unaccent(lacl.label) ILIKE unaccent('%' || :input || '%') " +
                    "AND NOT c.is_deleted " +
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

    @Query(
            nativeQuery = true,
            value = "SELECT cl.* FROM concept_label cl " +
                    "JOIN concept c ON cl.fk_concept_id = c.concept_id " +
                    "WHERE cl.fk_field_parent_concept_id = :parentConceptId " +
                    "AND cl.lang_code = :langCode " +
                    "AND NOT c.is_deleted " +
                    "LIMIT :limit"
    )
    List<ConceptLabel> findAllLabelsByParentConceptAndLangCode(Long parentConceptId, String langCode, int limit);
}
