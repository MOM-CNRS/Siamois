package fr.siamois.infrastructure.database.repositories.recordingunit;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationshipId;
import fr.siamois.domain.models.vocabulary.Concept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StratigraphicRelationshipRepository extends
        JpaRepository<StratigraphicRelationship, StratigraphicRelationshipId>,
        RevisionRepository<StratigraphicRelationship, StratigraphicRelationshipId, Long> {

    Optional<StratigraphicRelationship> findByUnit1AndUnit2AndType(RecordingUnit unit1, RecordingUnit unit2, Concept type);

    List<StratigraphicRelationship> findByUnit1AndType(RecordingUnit unit, Concept type);

    List<StratigraphicRelationship> findByUnit2AndType(RecordingUnit unit, Concept type);
}