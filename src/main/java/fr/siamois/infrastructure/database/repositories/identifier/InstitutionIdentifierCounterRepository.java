package fr.siamois.infrastructure.database.repositories.identifier;

import fr.siamois.domain.models.identifier.InstitutionIdentifierCounter;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstitutionIdentifierCounterRepository extends CrudRepository<InstitutionIdentifierCounter, Long> {
    @QueryHints(@QueryHint(name = HibernateHints.HINT_FLUSH_MODE, value = "COMMIT"))
    @Query(nativeQuery = true,
            value = "SELECT institution_identifier_nextval(:institutionId, :formConfigId, :canonicalKey, :minCode)")
    int nextValue(@Param("institutionId") Long institutionId,
                  @Param("formConfigId") Long formConfigId,
                  @Param("canonicalKey") String canonicalKey,
                  @Param("minCode") int minCode);

    @Query("""
            select c.counter from InstitutionIdentifierCounter c
            where c.institution.id = :institutionId and c.formConfig.id = :formConfigId
              and c.canonicalKey = :canonicalKey
            """)
    Optional<Integer> findCounter(@Param("institutionId") Long institutionId,
                                  @Param("formConfigId") Long formConfigId,
                                  @Param("canonicalKey") String canonicalKey);
}
