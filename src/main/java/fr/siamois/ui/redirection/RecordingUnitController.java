package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.panel.FlowBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;

/**
 * flow.xhtml (multi-panel/tab bar) is being retired — every entry point here now redirects through
 * {@link FlowBean#redirectToFocus} to the single-panel focus.xhtml instead of forwarding to flow.xhtml,
 * so the React main-panel/list migration (only reachable from focus.xhtml) is actually exercised.
 */
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
    public void toRecordingUnitList() throws IOException {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        flowBean.redirectToFocus("/recording-unit");
    }

    @GetMapping("/recording-unit/{id}")
    public void toRecordingUnit(@PathVariable Long id) throws IOException {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        flowBean.redirectToFocus("/recording-unit/" + id);
    }

    @GetMapping("/action-unit/{id}/recording-unit/new")
    public String newRecordingUnit(@PathVariable Long id) {
        // todo : open dialog
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FORWARD_FLOW_XHTML;
    }

}
