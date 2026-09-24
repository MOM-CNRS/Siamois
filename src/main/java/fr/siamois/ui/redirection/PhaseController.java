package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Scope(value = "session")
public class PhaseController {

    private final NavBean navBean;

    public PhaseController(NavBean navBean) {
        this.navBean = navBean;
    }

    @GetMapping("/phase")
    public String toPhaseList() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("phase");
    }

    @GetMapping("/phase/{id}")
    public String toPhase(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("phase/" + id);
    }

}
