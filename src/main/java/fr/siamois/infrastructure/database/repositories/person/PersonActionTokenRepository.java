package fr.siamois.infrastructure.database.repositories.person;

import fr.siamois.domain.models.auth.token.PersonActionToken;
import fr.siamois.domain.models.auth.token.PersonActionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonActionTokenRepository extends JpaRepository<PersonActionToken, Long> {

    boolean existsByToken(String token);

    Optional<PersonActionToken> findByTokenAndType(String token, PersonActionType type);

    Optional<PersonActionToken> findByPersonIdAndType(Long personId, PersonActionType type);

    void deleteByPersonIdAndType(Long personId, PersonActionType type);
}
