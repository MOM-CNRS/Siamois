package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface LocalizedConceptDataRepository extends CrudRepository<LocalizedConceptData, ConceptLabel.Id> {

    @Query(
            nativeQuery = true,
            value = "SELECT lcd.* FROM localized_concept_data lcd " +
                    "WHERE lcd.fk_field_parent_concept_id = :parentFieldConceptId " +
                    "AND lcd.lang_code = :langCode " +
                    "AND unaccent(lcd.label) ILIKE unaccent('%' || :input || '%') " +
                    "LIMIT :limit"
    )
    Set<LocalizedConceptData> findByParentConceptAndFieldCodeAndInputLimited(
            Long parentFieldConceptId,
            String langCode,
            String input,
            int limit);

    @Query(
            nativeQuery = true,
            value = "SELECT lcd.* FROM localized_concept_data lcd " +
                    "WHERE lcd.fk_concept_id = :conceptId " +
                    "AND lcd.lang_code = :langCode"
    )
    Optional<LocalizedConceptData> findByConceptAndLangCode(Long conceptId, String langCode);

    List<LocalizedConceptData> findAllByParentConcept(Concept parentConcept, Limit limit);

    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT ON (lcd.fk_concept_id) lcd.* FROM localized_concept_data lcd " +
                    "WHERE lcd.fk_field_parent_concept_id = :parentConceptId"
    )
    Set<LocalizedConceptData> findAllWithDistinctConceptByParentConcept(Long parentConceptId);

    Set<LocalizedConceptData> findAllByConcept(Concept concept);
}
