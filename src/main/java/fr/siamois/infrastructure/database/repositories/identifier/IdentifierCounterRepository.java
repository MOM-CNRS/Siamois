package fr.siamois.infrastructure.database.repositories.identifier;

import fr.siamois.domain.models.identifier.IdentifierCounter;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IdentifierCounterRepository extends CrudRepository<IdentifierCounter, Long> {
    @QueryHints(@QueryHint(name = HibernateHints.HINT_FLUSH_MODE, value = "COMMIT"))
    @Query(nativeQuery = true,
            value = "SELECT identifier_nextval(:actionUnitId, :formConfigId, :canonicalKey, :minCode)")
    int nextValue(@Param("actionUnitId") Long actionUnitId,
                  @Param("formConfigId") Long formConfigId,
                  @Param("canonicalKey") String canonicalKey,
                  @Param("minCode") int minCode);
}
