package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.phase.Phase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PhaseRepository extends JpaRepository<Phase, Long>, JpaSpecificationExecutor<Phase> {
}
