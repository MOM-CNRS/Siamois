package fr.siamois.infrastructure.database;

import fr.siamois.domain.models.uiview.FicheConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FicheConfigRepository extends JpaRepository<FicheConfig, Long> {

    /** The configuration of a (project, record type, sub-type); 0 stands for "none" in the last two ids. */
    @Query("select c from FicheConfig c where c.institution.id = :institutionId and c.resourceType = :resourceType "
            + "and coalesce(c.project.id, 0L) = :projectId and coalesce(c.typeId, 0L) = :typeId")
    Optional<FicheConfig> find(@Param("institutionId") Long institutionId,
                               @Param("resourceType") String resourceType,
                               @Param("projectId") Long projectId,
                               @Param("typeId") Long typeId);
}
