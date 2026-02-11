package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.panel.FlowBean;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SpecimenController {

    private final NavBean navBean;
    private final FlowBean flowBean;

    public SpecimenController(NavBean navBean, FlowBean flowBean) {
        this.navBean = navBean;
        this.flowBean = flowBean;
    }

    @GetMapping("/specimen")
    public String toSpecimenList() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        flowBean.addSpecimenListPanel();
        return "forward:/flow.xhtml";
    }

    @GetMapping("/specimen/{id}")
    public String toSpecimen(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        flowBean.addSpecimenPanel(id);
        return "forward:/flow.xhtml";
    }

}
