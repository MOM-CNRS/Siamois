package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.panel.FlowBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Scope(value = "session")
public class ActionUnitController {

    private final FlowBean flowBean;

    private static final String FLOW_FORWARD_PATH = "forward:/flow.xhtml";
    private final NavBean navBean;

    public ActionUnitController(FlowBean flowBean, NavBean navBean) {
        this.flowBean = flowBean;
        this.navBean = navBean;
    }

    @GetMapping("/action-unit/{id}")
    public String toActionUnit(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        flowBean.addActionUnitPanel(id);
        return FLOW_FORWARD_PATH;
    }

    @GetMapping("/action-unit")
    public String toActionUnitList() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        flowBean.addActionUnitListPanel();
        return FLOW_FORWARD_PATH;
    }

    @GetMapping("/spatial-unit/{id}/action-unit/new")
    public String addActionUnit(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FLOW_FORWARD_PATH;
    }

}
