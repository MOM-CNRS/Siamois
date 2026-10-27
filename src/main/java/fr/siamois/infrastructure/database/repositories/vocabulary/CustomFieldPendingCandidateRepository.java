package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.CustomFieldPendingCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomFieldPendingCandidateRepository extends JpaRepository<CustomFieldPendingCandidate, Long> {

    Optional<CustomFieldPendingCandidate> findByFieldIdAndVocabularyId(Long fieldId, Long vocabularyId);

    List<CustomFieldPendingCandidate> findAllByFieldId(Long fieldId);

    /** The candidates pending in a thesaurus, with their field, whose name is what the thesaurus is searched for. */
    @Query("""
            select p
            from CustomFieldPendingCandidate p
            join fetch p.field
            where p.vocabulary.id = :vocabularyId
            """)
    List<CustomFieldPendingCandidate> findAllByVocabularyId(@Param("vocabularyId") Long vocabularyId);
}
