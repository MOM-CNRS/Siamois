package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.study.Study;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface StudyRepository extends JpaRepository<Study, Long>, JpaSpecificationExecutor<Study> {

    /** Every study of a project: read by the export. */
    List<Study> findAllByActionUnitId(Long actionUnitId);

    boolean existsByActionUnitIdAndIdentifier(Long actionUnitId, String identifier);

    int countByActionUnitId(Long actionUnitId);

    List<Study> findByActionUnitIdAndIdentifier(Long actionUnitId, String identifier);

    Optional<Study> findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(Long actionUnitId, OffsetDateTime createdAt);

    Optional<Study> findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(Long actionUnitId, OffsetDateTime createdAt);

    Optional<Study> findFirstByActionUnitIdOrderByCreationTimeAsc(Long actionUnitId);

    Optional<Study> findFirstByActionUnitIdOrderByCreationTimeDesc(Long actionUnitId);
}
