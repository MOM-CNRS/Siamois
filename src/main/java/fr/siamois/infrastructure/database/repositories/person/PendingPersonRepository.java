package fr.siamois.infrastructure.database.repositories.person;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface PendingPersonRepository extends CrudRepository<PendingPerson, Long> {
    boolean existsByRegisterToken(String registerToken);

    Optional<PendingPerson> findByRegisterToken(String registerToken);

    Optional<PendingPerson> findByDisabledPersonId(Long disabledPersonId);

    boolean existsByDisabledPersonId(Long disabledPersonId);

    @Query("SELECT p.disabledPerson.id FROM PendingPerson p WHERE p.disabledPerson.id IN :personIds")
    Set<Long> findDisabledPersonIdsIn(@Param("personIds") Collection<Long> personIds);

    void deleteByDisabledPersonId(Long disabledPersonId);
}
