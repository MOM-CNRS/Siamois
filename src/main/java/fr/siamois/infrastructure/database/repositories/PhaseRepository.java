package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.phase.Phase;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface PhaseRepository extends CrudRepository<Phase, Long>, JpaSpecificationExecutor<Phase> {
    Optional<Phase> findByIdentifierAndActionUnitId(String identifier, Long actionUnitId);

    @Query(nativeQuery = true, value = "SELECT p.* " +
            "FROM phase p " +
            "JOIN recording_unit_phase rup ON p.phase_id = rup.fk_phase_id " +
            "WHERE rup.fk_recording_unit_id = :ruId")
    Set<Phase> findByRecordingUnitId(Long ruId);
}
