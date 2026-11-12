package fr.siamois.ui.api.openapi.v1.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.siamois.domain.models.misc.ProjectCreationProgress;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The project creations the REST clients follow. A creation is one blocking request, so its progress is read by
 * other requests: the client names the creation with a token of its own, sent with the creation and then asked
 * for while it runs. A progress is kept under the person who created it, so a token only reads its owner's
 * creation, and is forgotten some time after it was opened.
 */
@Component
public class ProjectCreationProgressRegistry {

    static final int MAX_TOKEN_LENGTH = 64;
    private static final Duration KEPT_FOR = Duration.ofMinutes(30);
    private static final long MAX_FOLLOWED = 1_000;

    /**
     * What names a followed creation: who runs it, and the token they chose for it.
     */
    private record Key(Long personId, String token) {
    }

    private final Cache<Key, ProjectCreationProgress> followed = Caffeine.newBuilder()
            .expireAfterWrite(KEPT_FOR)
            .maximumSize(MAX_FOLLOWED)
            .build();

    /**
     * Opens the progress of a creation. A creation sent without a usable token is not followed: its progress
     * is not kept.
     *
     * @param personId the person creating the project
     * @param token    the token the client follows the creation with, null when it does not follow it
     * @return the progress the creation reports on
     */
    public ProjectCreationProgress open(Long personId, String token) {
        ProjectCreationProgress progress = new ProjectCreationProgress();
        if (isUsable(token)) {
            followed.put(new Key(personId, token), progress);
        }
        return progress;
    }

    /**
     * How far the creation a person follows with a token is.
     *
     * @param personId the person asking
     * @param token    the token sent with the creation
     * @return the percentage of the creation, 0 when no creation of this person carries the token (yet)
     */
    public int percentageOf(Long personId, String token) {
        if (!isUsable(token)) {
            return 0;
        }
        ProjectCreationProgress progress = followed.getIfPresent(new Key(personId, token));
        return progress == null ? 0 : progress.getPercentage();
    }

    /**
     * Whether a token can name a creation: present, and short enough to be kept.
     */
    private static boolean isUsable(String token) {
        return token != null && !token.isBlank() && token.length() <= MAX_TOKEN_LENGTH;
    }
}
