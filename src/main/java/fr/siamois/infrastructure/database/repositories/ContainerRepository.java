package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContainerRepository extends JpaRepository<Container, Long>, JpaSpecificationExecutor<Container> {

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
