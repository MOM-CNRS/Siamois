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
            ExecutionContextHolder.clear();
        }
    }

    private void bindExecutionContext(HttpServletRequest request) {
        if (request.getSession(false) == null) {
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
