package fr.siamois.ui.config.handler;

import fr.siamois.domain.events.publisher.LoginEventPublisher;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.UpdatePasswordBean;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handler to manage the redirection after a successful login and setup the session.
 *
 * @author Julien Linget
 */
@Slf4j
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final SessionSettingsBean sessionSettingsBean;
    private final LoginEventPublisher loginEventPublisher;
    private final UpdatePasswordBean updatePasswordBean;

    public LoginSuccessHandler(SessionSettingsBean sessionSettingsBean, LoginEventPublisher loginEventPublisher, UpdatePasswordBean updatePasswordBean) {
        this.sessionSettingsBean = sessionSettingsBean;
        this.loginEventPublisher = loginEventPublisher;
        this.updatePasswordBean = updatePasswordBean;
    }

    /**
     * Redirects the user to the saved request URL if it exists, otherwise to the home page.
     * @param request The HTTP request
     * @param response The HTTP response
     * @param authentication The authentication object
     * @throws IOException If the redirection fails
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        sessionSettingsBean.setupSession();
        loginEventPublisher.publishLoginEvent();
        if (sessionSettingsBean.getAuthenticatedUser().isPassToModify()) {
            redirectToUpdatePassword(request, response);
        } else {
            redirectRequest(request, response);
        }
    }

    private void redirectToUpdatePassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Retrieve the saved request
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        SavedRequest savedRequest = requestCache.getRequest(request, response);

        // Get the context path
        String contextPath = request.getContextPath();

        String nextUrl;
        if (savedRequest != null) {
            nextUrl = savedRequest.getRedirectUrl();
        } else {
            nextUrl = contextPath + "/";
        }

        updatePasswordBean.init(sessionSettingsBean.getAuthenticatedUser(), nextUrl);

        // Redirect to the update password page
        response.sendRedirect(contextPath + "/pages/settings/updatePassword.xhtml");
    }

    private static void redirectRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Retrieve the saved request
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        SavedRequest savedRequest = requestCache.getRequest(request, response);

        // Get the context path
        String contextPath = request.getContextPath();

        // Redirect to the saved request URL if it exists, otherwise redirect to the login page
        if (savedRequest != null) {
            String targetUrl = savedRequest.getRedirectUrl();
            response.sendRedirect(targetUrl);
        } else {
            // welcome panel
            response.sendRedirect(contextPath + "/focus/L3dlbGNvbWU=");
        }
    }
}
