package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Scope(value = "session")
public class ContainerController {

    private final NavBean navBean;

    public ContainerController(NavBean navBean) {
        this.navBean = navBean;
    }

    @GetMapping("/container")
    public String toContainerList() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("container");
    }

    @GetMapping("/container/{id}")
    public String toContainer(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("container/" + id);
    }

}
