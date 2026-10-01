package fr.siamois.infrastructure.api.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Files each floating-button feedback report as a GitHub issue on the project repository. */
@Slf4j
@Service
public class GitHubFeedbackService {

    private static final String API_ROOT = "https://api.github.com";

    private final RestTemplate restTemplate;
    private final AtomicBoolean screenshotBranchReady = new AtomicBoolean(false);

    @Value("${siamois.feedback.github.token:}")
    private String token;

    @Value("${siamois.feedback.github.repository:MOM-CNRS/Siamois}")
    private String repository;

    @Value("${siamois.feedback.github.label:feedback}")
    private String label;

    @Value("${siamois.feedback.github.screenshot-branch:feedback-screenshots}")
    private String screenshotBranch;

    @Value("${siamois.feedback.github.issues-url:https://github.com/MOM-CNRS/Siamois/issues?q=is%3Aissue+label%3Afeedback}")
    private String issuesUrl;

    public String getIssuesUrl() {
        return issuesUrl;
    }

    /** Uses the JDK {@code HttpClient}-backed request factory rather than the app's default
     * {@code SimpleClientHttpRequestFactory} (built on {@code HttpURLConnection}), which rejects
     * PATCH ("Invalid HTTP method: PATCH") — GitHub's issue-update endpoint requires it. */
    public GitHubFeedbackService() {
        this.restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank();
    }

    /** Reference to a just-created issue, returned immediately so the reporter isn't kept waiting on
     * the (slower) screenshot upload — {@link #patchIssueBody} fills it in afterward. */
    public record IssueRef(String htmlUrl, long number) {}

    /** Creates the GitHub issue right away with a "screenshot pending" placeholder in the body, so the
     * reporter gets a fast confirmation; the real screenshot is attached asynchronously afterward via
     * {@link #uploadScreenshot} + {@link #patchIssueBody}. */
    public Optional<IssueRef> createIssue(String title, String bodyWithoutScreenshot) {
        if (!isConfigured()) {
            log.warn("GitHub feedback token not configured; discarding feedback \"{}\"", title);
            return Optional.empty();
        }

        String body = bodyWithoutScreenshot + "\n\n_📎 Envoi de la capture d'écran en cours..._";

        try {
            return Optional.ofNullable(postIssue(title, body, true))
                    .or(() -> Optional.ofNullable(postIssue(title, body, false)));
        } catch (RestClientException e) {
            log.error("Failed to create GitHub feedback issue \"{}\"", title, e);
            return Optional.empty();
        }
    }

    /** Replaces the placeholder body with its final text (screenshot markdown, or a failure notice)
     * once the upload has resolved one way or the other. */
    public void patchIssueBody(long issueNumber, String body) {
        try {
            restTemplate.exchange(
                    API_ROOT + "/repos/" + repository + "/issues/" + issueNumber,
                    HttpMethod.PATCH,
                    new HttpEntity<>(Map.of("body", body), jsonHeaders()),
                    Map.class);
        } catch (RestClientException e) {
            log.error("Failed to update feedback issue #{} with its screenshot", issueNumber, e);
        }
    }

    private IssueRef postIssue(String title, String body, boolean withLabel) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("body", body);
        if (withLabel) payload.put("labels", List.of(label));

        try {
            Map<?, ?> response = restTemplate.postForObject(
                    API_ROOT + "/repos/" + repository + "/issues",
                    new HttpEntity<>(payload, jsonHeaders()),
                    Map.class);
            Object htmlUrl = response == null ? null : response.get("html_url");
            Object number = response == null ? null : response.get("number");
            if (htmlUrl == null || number == null) return null;
            return new IssueRef(htmlUrl.toString(), Long.parseLong(number.toString()));
        } catch (RestClientException e) {
            if (withLabel) {
                log.warn("Issue creation with label \"{}\" failed, retrying without it", label, e);
                return null;
            }
            throw e;
        }
    }

    /** Uploads the screenshot to a dedicated branch and returns its raw.githubusercontent.com URL. */
    public Optional<String> uploadScreenshot(byte[] png) {
        try {
            ensureScreenshotBranchExists();

            String path = "feedback-screenshots/" + UUID.randomUUID() + ".png";
            Map<String, Object> payload = new HashMap<>();
            payload.put("message", "Add feedback screenshot");
            payload.put("content", Base64.getEncoder().encodeToString(png));
            payload.put("branch", screenshotBranch);

            restTemplate.exchange(
                    API_ROOT + "/repos/" + repository + "/contents/" + path,
                    HttpMethod.PUT,
                    new HttpEntity<>(payload, jsonHeaders()),
                    Map.class);

            return Optional.of("https://raw.githubusercontent.com/" + repository + "/" + screenshotBranch + "/" + path);
        } catch (RestClientException e) {
            log.error("Failed to upload feedback screenshot to GitHub", e);
            return Optional.empty();
        }
    }

    /** The screenshot branch is created once (from the default branch's HEAD) and reused afterward. */
    private void ensureScreenshotBranchExists() {
        if (screenshotBranchReady.get()) return;

        try {
            restTemplate.exchange(
                    API_ROOT + "/repos/" + repository + "/branches/" + screenshotBranch,
                    HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders()),
                    Map.class);
            screenshotBranchReady.set(true);
            return;
        } catch (RestClientException notFound) {
            log.info("Screenshot branch \"{}\" does not exist yet, creating it", screenshotBranch);
        }

        Map<?, ?> repoInfo = restTemplate.exchange(
                API_ROOT + "/repos/" + repository,
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class).getBody();
        String defaultBranch = repoInfo == null ? "main" : String.valueOf(repoInfo.get("default_branch"));

        Map<?, ?> refInfo = restTemplate.exchange(
                API_ROOT + "/repos/" + repository + "/git/ref/heads/" + defaultBranch,
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class).getBody();
        Map<?, ?> object = refInfo == null ? null : (Map<?, ?>) refInfo.get("object");
        String sha = object == null ? null : String.valueOf(object.get("sha"));

        Map<String, Object> newRefPayload = new HashMap<>();
        newRefPayload.put("ref", "refs/heads/" + screenshotBranch);
        newRefPayload.put("sha", sha);

        restTemplate.postForObject(
                API_ROOT + "/repos/" + repository + "/git/refs",
                new HttpEntity<>(newRefPayload, jsonHeaders()),
                Map.class);

        screenshotBranchReady.set(true);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return headers;
    }

}
