package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.phase.Phase;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PhaseRepository extends CrudRepository<Phase, Long>, JpaSpecificationExecutor<Phase> {
    boolean existsByActionUnitIdAndIdentifier(Long actionUnitId, String identifier);

    Optional<Phase> findByIdentifierAndActionUnitId(String identifier, Long actionUnitId);

    List<Phase> findAllByIdentifierInAndActionUnitId(Collection<String> identifiers, Long actionUnitId);
  
    @Query(nativeQuery = true, value = "SELECT p.* " +
            "FROM phase p " +
            "JOIN recording_unit_phase rup ON p.phase_id = rup.fk_phase_id " +
            "WHERE rup.fk_recording_unit_id = :ruId")
    Set<Phase> findByRecordingUnitId(Long ruId);

    /**
     * Recording-unit/phase edges of a whole page of units in a single query, for in-memory
     * grouping — avoids one {@link #findByRecordingUnitId} call per row.
     */
    @Query(nativeQuery = true, value = "SELECT rup.fk_recording_unit_id AS owner_id, rup.fk_phase_id AS related_id " +
            "FROM recording_unit_phase rup " +
            "WHERE rup.fk_recording_unit_id IN (:ruIds)")
    List<Object[]> findPhaseEdges(@Param("ruIds") Collection<Long> ruIds);
}
