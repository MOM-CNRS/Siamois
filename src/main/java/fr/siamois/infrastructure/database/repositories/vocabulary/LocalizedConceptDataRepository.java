package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.LocalizedConceptDataId;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface LocalizedConceptDataRepository extends CrudRepository<LocalizedConceptData, LocalizedConceptDataId> {

    @Query(
            nativeQuery = true,
            value = "WITH scored AS ( " +
                    "  SELECT lcd.*, similarity(lcd.label, :input) AS score " +
                    "  FROM localized_concept_data lcd " +
                    "  WHERE lcd.lang_code = :langCode " +
                    "    AND lcd.fk_field_parent_concept_id = :parentFieldConceptId " +
                    ")" +
                    "SELECT s.concept_definition, s.label, s.lang_code, s.fk_concept_id, s.fk_field_parent_concept_id FROM scored s " +
                    "WHERE s.score >= :minSimilarityScore " +
                    "ORDER BY s.score DESC " +
                    "LIMIT :limit"
    )
    Set<LocalizedConceptData> findConceptByFieldCodeAndInputLimit(
            Long parentFieldConceptId,
            String langCode,
            String input,
            double minSimilarityScore,
            int limit);

    Set<LocalizedConceptData> findLocalizedConceptDataByParentConceptAndLabelContaining(Concept parentConcept, String label);

    @Query(
            nativeQuery = true,
            value = "SELECT lcd.* FROM localized_concept_data lcd " +
                    "WHERE lcd.fk_field_parent_concept_id = :parentConceptId " +
                    "AND lcd.lang_code = :langCode"
    )
    Set<LocalizedConceptData> findAllByParentConceptAndLangCode(Long parentConceptId, String langCode);

    Set<LocalizedConceptData> findAllByParentConcept(Concept parentConcept);

    @Query(
            nativeQuery = true,
            value = "SELECT lcd.* FROM localized_concept_data lcd " +
                    "WHERE lcd.fk_concept_id = :conceptId " +
                    "AND lcd.lang_code = :langCode"
    )
    Optional<LocalizedConceptData> findByConceptAndLangCode(Long conceptId, String langCode);

    List<LocalizedConceptData> findAllByParentConcept(Concept parentConcept, Limit limit);

    List<LocalizedConceptData> findAllByConcept(Concept concept);
}
