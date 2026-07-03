package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.phase.Phase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PhaseRepository extends JpaRepository<Phase, Long>, JpaSpecificationExecutor<Phase> {
    Optional<Phase> findByIdentifierAndActionUnitId(String identifier, Long actionUnitId);

    List<Phase> findAllByIdentifierInAndActionUnitId(Collection<String> identifiers, Long actionUnitId);
}
