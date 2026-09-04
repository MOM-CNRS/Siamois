package fr.siamois.ui.bean.dialog.snake;

import fr.siamois.domain.models.GameScore;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.repositories.GameScoreRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.AuthenticatedUserUtils;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Backs the "motherlode" Snake easter egg dialog (see {@code SearchBean#completeText}, which opens
 * it) — persists each run's score for the current user and active institution via {@link GameScoreRepository}.
 */
@Slf4j
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
@Getter
public class SnakeGameBean implements Serializable {

    private static final String GAME_ID = "snake-motherload";
    private static final int LEADERBOARD_SIZE = 10;

    private final transient GameScoreRepository gameScoreRepository;
    private final transient InstitutionRepository institutionRepository;
    private final SessionSettingsBean sessionSettingsBean;

    private int lastScore;
    private int bestScore;
    private transient List<GameScoreRepository.GameLeaderboardEntry> leaderboard = List.of();

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

    private Long activeInstitutionId() {
        return sessionSettingsBean.getSelectedInstitution() == null
                ? null : sessionSettingsBean.getSelectedInstitution().getId();
    }

    private void saveScore(int score) {
        Optional<Person> person = AuthenticatedUserUtils.getAuthenticatedUser();
        Long institutionId = activeInstitutionId();
        if (person.isEmpty() || institutionId == null) {
            log.warn("Snake score of {} discarded: no authenticated user or active institution", score);
            return;
        }
        Optional<Institution> institution = institutionRepository.findById(institutionId);
        if (institution.isEmpty()) {
            log.warn("Snake score of {} discarded: active institution {} not found", score, institutionId);
            return;
        }

        GameScore gameScore = new GameScore();
        gameScore.setPerson(person.get());
        gameScore.setInstitution(institution.get());
        gameScore.setGame(GAME_ID);
        gameScore.setScore(score);
        gameScoreRepository.save(gameScore);

        lastScore = score;
        bestScore = gameScoreRepository.findTopByPersonAndGameAndInstitutionIdOrderByScoreDesc(person.get(), GAME_ID, institutionId)
                .map(GameScore::getScore)
                .orElse(score);
        leaderboard = gameScoreRepository.findLeaderboard(GAME_ID, institutionId, PageRequest.of(0, LEADERBOARD_SIZE));
    }
}
