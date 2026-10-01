package fr.siamois.ui.bean.dialog.feedback;

import fr.siamois.infrastructure.api.github.GitHubFeedbackAsyncRunner;
import fr.siamois.infrastructure.api.github.GitHubFeedbackService;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Base64;
import java.util.Optional;

/** Backs the floating feedback button available on every page: captures a screen region and a
 * description (with an optional reporter name — no email, since the resulting issues are public),
 * then files them as a GitHub issue via {@link GitHubFeedbackService}. */
@Slf4j
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
public class FeedbackBean implements Serializable {

    private static final int TITLE_MAX_LENGTH = 80;

    private final transient GitHubFeedbackService gitHubFeedbackService;
    private final transient GitHubFeedbackAsyncRunner gitHubFeedbackAsyncRunner;
    private final transient Optional<BuildProperties> buildProperties;

    @Autowired
    public FeedbackBean(GitHubFeedbackService gitHubFeedbackService,
                         GitHubFeedbackAsyncRunner gitHubFeedbackAsyncRunner,
                         Optional<BuildProperties> buildProperties) {
        this.gitHubFeedbackService = gitHubFeedbackService;
        this.gitHubFeedbackAsyncRunner = gitHubFeedbackAsyncRunner;
        this.buildProperties = buildProperties;
    }

    public String getIssuesUrl() {
        return gitHubFeedbackService.getIssuesUrl();
    }

    /** Client id the dedicated {@code feedbackGrowl} listens to, so the app-wide growl in the
     * template doesn't also render (and HTML-escape) these messages. */
    private static final String GROWL_CLIENT_ID = "feedbackToast";

    /** Called by the client-side capture overlay's {@code p:remoteCommand}; image, description and
     * the optional reporter identity are forwarded as request params, the page URL is read from the
     * browser location so it always matches what the user actually saw. */
    public void saveFeedbackFromRequest() {
        FacesContext context = FacesContext.getCurrentInstance();
        var params = context.getExternalContext().getRequestParameterMap();

        String name = params.get("feedbackName");
        String description = params.get("feedbackDescription");
        String pageUrl = params.get("feedbackPageUrl");
        String imageDataUrl = params.get("feedbackImage");
        byte[] screenshot = decodeImage(imageDataUrl);

        if (description == null || description.isBlank() || screenshot.length == 0) {
            context.addMessage(GROWL_CLIENT_ID, new FacesMessage(FacesMessage.SEVERITY_WARN,
                    "Retour non enregistré", "La description et la capture d'écran sont obligatoires."));
            return;
        }

        String title = buildTitle(description);
        String body = buildBody(description, name, pageUrl);

        Optional<GitHubFeedbackService.IssueRef> issue = gitHubFeedbackService.createIssue(title, body);

        if (issue.isPresent() && issue.get().htmlUrl().startsWith("https://github.com/")) {
            gitHubFeedbackAsyncRunner.attachScreenshotAsync(issue.get().number(), body, screenshot);

            String detail = "Merci pour votre retour ! <a href=\"" + issue.get().htmlUrl() + "\" target=\"_blank\" rel=\"noopener\">Voir l'issue sur GitHub →</a>";
            context.addMessage(GROWL_CLIENT_ID, new FacesMessage(FacesMessage.SEVERITY_INFO, "Retour enregistré", detail));
        } else {
            context.addMessage(GROWL_CLIENT_ID, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Retour non enregistré", "La création de l'issue GitHub a échoué. Merci de réessayer plus tard."));
        }
    }

    private String buildTitle(String description) {
        String flattened = description.strip().replaceAll("\\s+", " ");
        if (flattened.length() <= TITLE_MAX_LENGTH) return flattened;
        return flattened.substring(0, TITLE_MAX_LENGTH - 1).stripTrailing() + "…";
    }

    private String buildBody(String description, String name, String pageUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Page :** ").append(pageUrl).append('\n');
        sb.append("**Version :** ").append(buildProperties.map(BuildProperties::getVersion).orElse("dev")).append('\n');

        if (name != null && !name.isBlank()) sb.append("**Rapporté par :** ").append(name.strip()).append('\n');

        sb.append('\n').append(description);
        return sb.toString();
    }

    private static byte[] decodeImage(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) return new byte[0];
        int comma = dataUrl.indexOf(',');
        String base64 = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            log.warn("Could not decode feedback screenshot", e);
            return new byte[0];
        }
    }

}
