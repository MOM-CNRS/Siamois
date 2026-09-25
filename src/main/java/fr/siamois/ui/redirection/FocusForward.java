package fr.siamois.ui.redirection;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Builds the Spring MVC forward to {@code /pages/focus.xhtml} for a plain app route
 * ("recording-unit/12", "phase"): FocusViewBean.beforeInit() decodes the {@code main} token and
 * builds the panel itself via PanelFactory. It is the only page that renders panels, so every
 * app route — including the ones the React panel pushes with history.pushState — lands here.
 */
final class FocusForward {

    private static final String FOCUS_FORWARD_PREFIX = "forward:/pages/focus.xhtml?main=";

    private FocusForward() {
    }

    static String to(String resourceUri) {
        String path = resourceUri.startsWith("/") ? resourceUri.substring(1) : resourceUri;
        return FOCUS_FORWARD_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(path.getBytes(StandardCharsets.UTF_8));
    }
}
