package fr.siamois.infrastructure.database.repositories.person;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface PendingPersonRepository extends CrudRepository<PendingPerson, Long> {
    boolean existsByRegisterToken(String registerToken);

    Optional<PendingPerson> findByRegisterToken(String registerToken);

    Optional<PendingPerson> findByDisabledPersonId(Long disabledPersonId);

    boolean existsByDisabledPersonIdAndRegisterTokenIsNotNull(Long disabledPersonId);

    /** Among the given persons, the ones invited by e-mail whose registration is still pending. */
    @Query("SELECT p.disabledPerson.id FROM PendingPerson p WHERE p.disabledPerson.id IN :personIds AND p.registerToken IS NOT NULL")
    Set<Long> findDisabledPersonIdsIn(@Param("personIds") Collection<Long> personIds);

    /** Among the given persons, the ones created from their name only, still awaiting the creation of their account. */
    @Query("SELECT p.disabledPerson.id FROM PendingPerson p WHERE p.disabledPerson.id IN :personIds AND p.registerToken IS NULL")
    Set<Long> findAwaitingAccountPersonIdsIn(@Param("personIds") Collection<Long> personIds);

    @Query("SELECT p.disabledPerson.id FROM PendingPerson p WHERE p.disabledPerson.id IN :personIds AND p.pendingInvitationExpirationDate < :now")
    Set<Long> findExpiredDisabledPersonIdsIn(@Param("personIds") Collection<Long> personIds, @Param("now") OffsetDateTime now);

    void deleteByDisabledPersonId(Long disabledPersonId);
}
