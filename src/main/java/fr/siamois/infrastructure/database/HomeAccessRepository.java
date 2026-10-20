package fr.siamois.infrastructure.database;

import fr.siamois.domain.models.uiview.HomeAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HomeAccessRepository extends JpaRepository<HomeAccess, Long> {

    /** An organisation's accesses, in display order, with their view. */
    @Query("select a from HomeAccess a join fetch a.view where a.institution.id = :institutionId order by a.position, a.id")
    List<HomeAccess> findAllOf(@Param("institutionId") Long institutionId);

    Optional<HomeAccess> findByIdAndInstitutionId(Long id, Long institutionId);

    @Query("select coalesce(max(a.position), 0) from HomeAccess a where a.institution.id = :institutionId")
    int maxPosition(@Param("institutionId") Long institutionId);
}
