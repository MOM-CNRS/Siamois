package fr.siamois.ui.bean.dialog.snake;

import fr.siamois.domain.models.GameScore;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.infrastructure.database.repositories.GameScoreRepository;
import fr.siamois.utils.AuthenticatedUserUtils;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Optional;

/**
 * Backs the "motherload" Snake easter egg dialog (see {@code SearchBean#completeText}, which opens
 * it) — persists each run's score for the current user via {@link GameScoreRepository}.
 */
@Slf4j
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
@Getter
public class SnakeGameBean implements Serializable {

    private static final String GAME_ID = "snake-motherload";

    private final transient GameScoreRepository gameScoreRepository;

    private int lastScore;
    private int bestScore;

    /** Called by the dialog's {@code p:remoteCommand}, which forwards the JS-tracked score as a request param. */
    public void saveScoreFromRequest() {
        String raw = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("score");
        int score;
        try {
            score = raw == null ? 0 : Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            score = 0;
        }
        saveScore(score);
    }

    private void saveScore(int score) {
        Optional<Person> person = AuthenticatedUserUtils.getAuthenticatedUser();
        if (person.isEmpty()) {
            log.warn("Snake score of {} discarded: no authenticated user", score);
            return;
        }

        GameScore gameScore = new GameScore();
        gameScore.setPerson(person.get());
        gameScore.setGame(GAME_ID);
        gameScore.setScore(score);
        gameScoreRepository.save(gameScore);

        lastScore = score;
        bestScore = gameScoreRepository.findTopByPersonAndGameOrderByScoreDesc(person.get(), GAME_ID)
                .map(GameScore::getScore)
                .orElse(score);
    }
}
