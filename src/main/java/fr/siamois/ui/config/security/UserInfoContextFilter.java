package fr.siamois.ui.config.security;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.AuthenticatedUserUtils;
import fr.siamois.utils.context.ExecutionContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds the session's {@link UserInfo} to the thread serving the current web request, so that code with
 * no access to the JSF beans — JPA entity listeners, domain services — can still tell which person and
 * institution it is acting for.
 * <p>
 * {@link ExecutionContextHolder} is thread-bound, not session-bound: setting it once at login would only
 * cover the login request, and would then stay stuck to that pooled thread for whoever it serves next.
 * This filter is what makes the holder correct on every request and empty again afterwards.
 * <p>
 * Registered in the JSF security chain <em>before</em> the authentication filter, so that its
 * {@code finally} also covers the login and logout requests, whose handlers bind a context of their own
 * (see {@link SessionSettingsBean#setupSession()}). The security context itself is already available at
 * that point, since {@code SecurityContextHolderFilter} runs earlier still.
 */
@RequiredArgsConstructor
public class UserInfoContextFilter extends OncePerRequestFilter {

    private final SessionSettingsBean sessionSettingsBean;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            bindExecutionContext(request);
            filterChain.doFilter(request, response);
        } finally {
            // Unconditional, and the only place allowed to skip a null-check: the worker goes straight
            // back to the pool after this, and anything still bound would be read by the next user it
            // serves. Also covers the contexts bound by the login and institution/language handlers.
            ExecutionContextHolder.clear();
        }
    }

    private void bindExecutionContext(HttpServletRequest request) {
        if (request.getSession(false) == null) {
            // Touching the session-scoped SessionSettingsBean would create a session. Anonymous traffic
            // (login page, static resources, health checks) must not get one.
            return;
        }
        if (AuthenticatedUserUtils.getAuthenticatedUser().isEmpty()) {
            return;
        }
        UserInfo userInfo = sessionSettingsBean.getUserInfo();
        if (userInfo != null) {
            ExecutionContextHolder.set(userInfo);
        }
    }
}
