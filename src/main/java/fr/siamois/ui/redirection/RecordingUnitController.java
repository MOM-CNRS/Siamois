package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Scope(value = "session")
public class RecordingUnitController {

    private final NavBean navBean;

    public RecordingUnitController(NavBean navBean) {
        this.navBean = navBean;
    }

    @GetMapping("/recording-unit")
    public String toRecordingUnitList() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("recording-unit");
    }

    @GetMapping("/recording-unit/{id}")
    public String toRecordingUnit(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("recording-unit/" + id);
    }

    // Creation happens in the new-unit dialog, not on a page of its own: land on the parent project.
    @GetMapping("/action-unit/{id}/recording-unit/new")
    public String newRecordingUnit(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("action-unit/" + id);
    }

}
