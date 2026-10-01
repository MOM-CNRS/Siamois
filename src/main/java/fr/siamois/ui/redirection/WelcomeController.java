package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class WelcomeController {

    private final NavBean navBean;

    public WelcomeController(NavBean navBean) {
        this.navBean = navBean;
    }

    @GetMapping({"/welcome", "/dashboard"})
    public String toWelcome() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("welcome");
    }

}
