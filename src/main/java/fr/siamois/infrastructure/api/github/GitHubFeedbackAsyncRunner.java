package fr.siamois.infrastructure.api.github;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Runs the screenshot upload + issue-body patch on a background thread, after
 * {@link GitHubFeedbackService#createIssue} has already returned the issue to the user — the reporter
 * doesn't wait on GitHub's slower "commit a file" call before seeing a confirmation. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubFeedbackAsyncRunner {

    private final GitHubFeedbackService gitHubFeedbackService;

    @Async("feedbackTaskExecutor")
    public void attachScreenshotAsync(long issueNumber, String bodyWithoutScreenshot, byte[] screenshotPng) {
        Optional<String> imageUrl = gitHubFeedbackService.uploadScreenshot(screenshotPng);

        String finalBody = bodyWithoutScreenshot + (imageUrl.isPresent()
                ? "\n\n![Capture d'écran](" + imageUrl.get() + ")"
                : "\n\n_⚠️ L'envoi de la capture d'écran a échoué._");

        gitHubFeedbackService.patchIssueBody(issueNumber, finalBody);
    }

}
