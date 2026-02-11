package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.panel.FlowBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Scope(value = "session")
public class RecordingUnitController {

    public static final String FORWARD_FLOW_XHTML = "forward:/flow.xhtml";
    private final FlowBean flowBean;
    private final NavBean navBean;

    public RecordingUnitController(FlowBean flowBean, NavBean navBean) {
        this.flowBean = flowBean;
        this.navBean = navBean;
    }

    @GetMapping("/recording-unit")
    public String toRecordingUnitList() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        flowBean.addRecordingUnitListPanel();
        return FORWARD_FLOW_XHTML;
    }

    @GetMapping("/recording-unit/{id}")
    public String toRecordingUnit(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        flowBean.addRecordingUnitPanel(id);
        return FORWARD_FLOW_XHTML;
    }

    @GetMapping("/action-unit/{id}/recording-unit/new")
    public String newRecordingUnit(@PathVariable Long id) {
        // todo : open dialog
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FORWARD_FLOW_XHTML;
    }

}
