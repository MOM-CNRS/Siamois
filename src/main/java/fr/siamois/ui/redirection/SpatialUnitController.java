package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Scope(value = "session")
public class SpatialUnitController {

    private final NavBean navBean;

    public SpatialUnitController(NavBean navBean) {
        this.navBean = navBean;
    }

    @GetMapping("/spatial-unit")
    public String toSpatialUnitList() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("spatial-unit");
    }

    @GetMapping("/spatial-unit/{id}")
    public String toSpatialUnit(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("spatial-unit/" + id);
    }

}
