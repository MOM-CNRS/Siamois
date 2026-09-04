package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.GameScore;
import fr.siamois.domain.models.auth.Person;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameScoreRepository extends CrudRepository<GameScore, Long> {
    Optional<GameScore> findTopByPersonAndGameAndInstitutionIdOrderByScoreDesc(Person person, String game, Long institutionId);

    /** Best score per person, scoped strictly to runs played under the given institution. */
    @Query("""
            SELECT gs.person.id AS personId,
                   gs.person.name AS name,
                   gs.person.lastname AS lastname,
                   MAX(gs.score) AS bestScore
            FROM GameScore gs
            WHERE gs.game = :game
              AND gs.institution.id = :institutionId
            GROUP BY gs.person.id, gs.person.name, gs.person.lastname
            ORDER BY MAX(gs.score) DESC
            """)
    List<GameLeaderboardEntry> findLeaderboard(@Param("game") String game,
                                                @Param("institutionId") Long institutionId,
                                                Pageable pageable);

    interface GameLeaderboardEntry {
        Long getPersonId();
        String getName();
        String getLastname();
        int getBestScore();
    }
}
