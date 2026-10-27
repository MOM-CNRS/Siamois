package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.Vocabulary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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
                    JOIN documentation_thesaurus dt ON dt.fk_vocabulary_id = v.vocabulary_id
                    WHERE dt.fk_institution_id = :institutionId
                    ORDER BY v.vocabulary_id
                    """
    )
    List<Vocabulary> findDistinctByInstitutionId(@Param("institutionId") Long institutionId);

    /**
     * The documentation thesauri of the given organisations (set at the organisation level) and of the given
     * projects (one by one, or every project of an organisation).
     */
    @Query("""
            SELECT DISTINCT v
            FROM DocumentationThesaurus dt
            JOIN dt.vocabulary v
            WHERE (dt.actionUnit IS NULL
                   AND (:allInstitutions = true OR dt.institution.id IN :institutionIds))
               OR (dt.actionUnit IS NOT NULL
                   AND (:allProjects = true
                        OR dt.institution.id IN :projectInstitutionIds
                        OR dt.actionUnit.id IN :actionUnitIds))
            ORDER BY v.id
            """)
    List<Vocabulary> findDocumentationThesauriOf(@Param("allInstitutions") boolean allInstitutions,
                                                 @Param("institutionIds") Collection<Long> institutionIds,
                                                 @Param("allProjects") boolean allProjects,
                                                 @Param("projectInstitutionIds") Collection<Long> projectInstitutionIds,
                                                 @Param("actionUnitIds") Collection<Long> actionUnitIds);

    /**
     * Find a vocabulary by its baseUri and externalId ignoring case.
     * @param baseUri The baseUri of the vocabulary
     * @param externalId The externalId of the vocabulary
     * @return An optional containing the vocabulary if found
     */
    @Query(
            value = "SELECT v FROM Vocabulary v WHERE upper(v.baseUri) = upper(:baseUri) AND upper(v.externalVocabularyId) = upper(:externalId) ORDER BY v.id"
    )
    List<Vocabulary> findAllByBaseUriAndVocabExternalId(@Param("baseUri")String baseUri, @Param("externalId") String externalId);

    /** The lookup ignores case, so legacy duplicates may match: the oldest one wins instead of failing. */
    default Optional<Vocabulary> findVocabularyByBaseUriAndVocabExternalId(String baseUri, String externalId) {
        return findAllByBaseUriAndVocabExternalId(baseUri, externalId).stream().findFirst();
    }

}

