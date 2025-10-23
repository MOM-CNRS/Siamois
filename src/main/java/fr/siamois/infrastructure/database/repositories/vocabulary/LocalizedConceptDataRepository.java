package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.LocalizedConceptDataId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

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
                    "SELECT s.label_id, s.concept_definition, s.label, s.lang_code, s.fk_concept_id, s.fk_field_parent_concept_id FROM scored s " +
                    "WHERE s.score >= :minSimilarityScore " +
                    "ORDER BY s.score DESC"
    )
    Set<LocalizedConceptData> findConceptByFieldcodeAndLabelInputWithSimilarity(
            Long parentFieldConceptId,
            String langCode,
            String input,
            double minSimilarityScore);

    @Query(
            nativeQuery = true,
            value = "WITH scored AS ( " +
                    "  SELECT lcd.*, similarity(lcd.label, :input) AS score " +
                    "  FROM localized_concept_data lcd " +
                    "  WHERE lcd.fk_field_parent_concept_id = :parentFieldConceptId " +
                    ")" +
                    "SELECT s.label_id, s.concept_definition, s.label, s.lang_code, s.fk_concept_id, s.fk_field_parent_concept_id FROM scored s " +
                    "WHERE s.score >= :minSimilarityScore " +
                    "ORDER BY s.score DESC"
    )
    Set<LocalizedConceptData> findConceptByFieldcodeAndLabelInputWithSimilarityNoLang(
            Long parentFieldConceptId,
            String input,
            double minSimilarityScore);

    @Query(
            nativeQuery = true,
            value = "SELECT lcd.* FROM localized_concept_data lcd " +
                    "WHERE lcd.fk_field_parent_concept_id = :parentConceptId " +
                    "AND lcd.lang_code = :langCode"
    )
    Set<LocalizedConceptData> findAllByParentConceptAndLangCode(Concept parentConcept, String langCode);

    Set<LocalizedConceptData> findAllByParentConcept(Concept parentConcept);

    @Query(
            nativeQuery = true,
            value = "SELECT lcd.* FROM localized_concept_data lcd " +
                    "WHERE lcd.lang_code = :langCode " +
                    "AND lcd.fk_field_parent_concept_id = :#{#parentConcept.id} " +
                    "AND lcd.label ILIKE '%' || :label || '%'"
    )
    Set<LocalizedConceptData> findAllByLangCodeAndParentConceptAndLabelContaining(String langCode, Concept parentConcept, String label);

    @Query(
            nativeQuery = true,
            value = "SELECT lcd.* FROM localized_concept_data lcd " +
                    "WHERE lcd.fk_concept_id = :#{#concept.id} " +
                    "AND lcd.lang_code = :langCode"
    )
    Optional<LocalizedConceptData> findByConceptAndLangCode(Concept concept, String langCode);
}
