package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.container.Container;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ContainerRepository extends JpaRepository<Container, Long>, JpaSpecificationExecutor<Container> {

    boolean existsByActionUnitIdAndIdentifier(Long actionUnitId, String identifier);

    List<Container> findByActionUnitIdAndIdentifier(Long actionUnitId, String identifier);

    Optional<Container> findFirstByActionUnitIdAndCreationTimeAfterOrderByCreationTimeAsc(Long actionUnitId, OffsetDateTime createdAt);

    Optional<Container> findFirstByActionUnitIdAndCreationTimeBeforeOrderByCreationTimeDesc(Long actionUnitId, OffsetDateTime createdAt);

    Optional<Container> findFirstByActionUnitIdOrderByCreationTimeAsc(Long actionUnitId);

    Optional<Container> findFirstByActionUnitIdOrderByCreationTimeDesc(Long actionUnitId);

    @Query(value = """
    WITH RECURSIVE ascend AS (
        /* 1. Start with the IDs provided in the array */
        SELECT container_id, fk_parent_id
        FROM container
        WHERE container_id = ANY(:seedIds)
        
        UNION
        
        /* 2. Join the container table where the ID is the parent 
              ID from the previous step of the recursion */
        SELECT c.container_id, c.fk_parent_id
        FROM container c
        INNER JOIN ascend a ON c.container_id = a.fk_parent_id
    )
    /* 3. Return only the unique IDs found in the path */
    SELECT DISTINCT container_id FROM ascend
    """, nativeQuery = true)
    List<Long> findAncestorClosure(@Param("seedIds") Long[] seedIds);
}
