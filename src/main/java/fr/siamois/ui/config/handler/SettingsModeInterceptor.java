package fr.siamois.ui.config.handler;

import fr.siamois.ui.bean.NavBean;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class SettingsModeInterceptor implements HandlerInterceptor {

    private final NavBean navBean;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        boolean isSettings = request.getRequestURI().contains("/settings");

        if(isSettings) {
            navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        }

        return true;
    }
}
