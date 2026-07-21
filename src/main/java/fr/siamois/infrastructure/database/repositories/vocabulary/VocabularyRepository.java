package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.Vocabulary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VocabularyRepository extends CrudRepository<Vocabulary, Long>, RevisionRepository<Vocabulary, Long, Long> {

    @Query("SELECT v FROM Vocabulary v ORDER BY v.id")
    List<Vocabulary> findAllOrderById();

    @Query(
            nativeQuery = true,
            value = """
                    SELECT DISTINCT v.*
                    FROM vocabulary v
                    JOIN concept c ON c.fk_vocabulary_id = v.vocabulary_id
                    JOIN concept_field_config cfc ON cfc.fk_concept_id = c.concept_id
                    WHERE cfc.fk_institution_id = :institutionId
                    ORDER BY v.vocabulary_id
                    """
    )
    List<Vocabulary> findDistinctByInstitutionId(@Param("institutionId") Long institutionId);

    /**
     * Find a vocabulary by its baseUri and externalId ignoring case.
     * @param baseUri The baseUri of the vocabulary
     * @param externalId The externalId of the vocabulary
     * @return An optional containing the vocabulary if found
     */
    @Query(
            value = "SELECT v FROM Vocabulary v WHERE upper(v.baseUri) = upper(:baseUri) AND upper(v.externalVocabularyId) = upper(:externalId)"
    )
    Optional<Vocabulary> findVocabularyByBaseUriAndVocabExternalId(@Param("baseUri")String baseUri, @Param("externalId") String externalId);

    /**
     * Find a vocabulary by its user and externalId ignoring case.
     * @param personId  The id of the user
     * @param categoryFieldCode The code of the field
     * @return An optional containing the vocabulary if found
     */
    @Query(
            nativeQuery = true,
            value = "SELECT v.* FROM vocabulary v " +
                    "JOIN field f ON v.vocabulary_id = f.fk_vocabulary_id " +
                    "WHERE f.fk_user_id = :personId AND f.field_code = :categoryFieldCode"
    )
    Optional<Vocabulary> findVocabularyOfUserForField(@Param("personId") Long personId, @Param("categoryFieldCode") String categoryFieldCode);

}

