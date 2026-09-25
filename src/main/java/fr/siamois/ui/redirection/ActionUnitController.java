package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Scope(value = "session")
public class ActionUnitController {

    private final NavBean navBean;

    public ActionUnitController(NavBean navBean) {
        this.navBean = navBean;
    }

    @GetMapping("/action-unit/{id}")
    public String toActionUnit(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("action-unit/" + id);
    }

    @GetMapping("/action-unit")
    public String toActionUnitList() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("action-unit");
    }

    // Creation happens in the new-unit dialog, not on a page of its own: land on the parent place.
    @GetMapping("/spatial-unit/{id}/action-unit/new")
    public String addActionUnit(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("spatial-unit/" + id);
    }

}
