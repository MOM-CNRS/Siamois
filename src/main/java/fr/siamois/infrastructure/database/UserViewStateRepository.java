package fr.siamois.infrastructure.database;

import fr.siamois.domain.models.uiview.UserViewState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserViewStateRepository extends JpaRepository<UserViewState, Long> {

    Optional<UserViewState> findByPersonIdAndInstitutionIdAndViewKey(Long personId, Long institutionId, String viewKey);

    @Modifying
    @Query("delete from UserViewState s where s.person.id = :personId and s.institution.id = :institutionId "
            + "and s.viewKey = :viewKey")
    int deleteOne(@Param("personId") Long personId, @Param("institutionId") Long institutionId,
                  @Param("viewKey") String viewKey);

    /** Drops what everybody had changed on a view that no longer exists. */
    @Modifying
    @Query("delete from UserViewState s where s.institution.id = :institutionId and s.viewKey = :viewKey")
    int deleteForAllUsers(@Param("institutionId") Long institutionId, @Param("viewKey") String viewKey);
}
