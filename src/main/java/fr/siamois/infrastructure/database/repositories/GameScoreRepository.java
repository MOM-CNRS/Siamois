package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.GameScore;
import fr.siamois.domain.models.auth.Person;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameScoreRepository extends CrudRepository<GameScore, Long> {
    Optional<GameScore> findTopByPersonAndGameOrderByScoreDesc(Person person, String game);
}
