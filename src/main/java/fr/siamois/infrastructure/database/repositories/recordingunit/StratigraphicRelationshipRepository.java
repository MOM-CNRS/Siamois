package fr.siamois.infrastructure.database.repositories.recordingunit;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationshipId;
import fr.siamois.domain.models.vocabulary.Concept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StratigraphicRelationshipRepository extends
        JpaRepository<StratigraphicRelationship, StratigraphicRelationshipId>,
        RevisionRepository<StratigraphicRelationship, StratigraphicRelationshipId, Long> {

    Optional<StratigraphicRelationship> findByUnit1AndUnit2(RecordingUnit unit1, RecordingUnit unit2);

    Optional<StratigraphicRelationship> findByUnit1AndUnit2AndConcept(RecordingUnit unit1, RecordingUnit unit2, Concept type);

    List<StratigraphicRelationship> findByUnit1AndConcept(RecordingUnit unit, Concept type);

    List<StratigraphicRelationship> findByUnit2AndConcept(RecordingUnit unit, Concept type);

    @Query("SELECT r FROM StratigraphicRelationship r WHERE r.unit1.id = :recordingUnitId OR r.unit2.id = :recordingUnitId")
    List<StratigraphicRelationship> findAllInvolvingRecordingUnitId(@Param("recordingUnitId") Long recordingUnitId);

    /** Broad prefetch by unit1 id — callers narrow to the exact (unit1, unit2) pair themselves (see {@link #findByUnit1AndUnit2}). */
    List<StratigraphicRelationship> findAllByUnit1IdIn(Collection<Long> unit1Ids);
}