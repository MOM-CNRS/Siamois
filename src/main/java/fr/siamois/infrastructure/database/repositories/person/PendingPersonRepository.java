package fr.siamois.infrastructure.database.repositories.person;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface PendingPersonRepository extends CrudRepository<PendingPerson, Long> {
    boolean existsByRegisterToken(String registerToken);

    Optional<PendingPerson> findByRegisterToken(String registerToken);

    void deleteByDisabledPersonId(Long disabledPersonId);
}
