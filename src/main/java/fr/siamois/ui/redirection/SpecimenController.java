package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Scope(value = "session")
public class SpecimenController {

    private final NavBean navBean;

    public SpecimenController(NavBean navBean) {
        this.navBean = navBean;
    }

    @GetMapping("/specimen")
    public String toSpecimenList() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("specimen");
    }

    @GetMapping("/specimen/{id}")
    public String toSpecimen(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("specimen/" + id);
    }

}
