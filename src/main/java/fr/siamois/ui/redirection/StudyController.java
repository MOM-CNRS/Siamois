package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** The pages of the studies: their list ({@code /study}) and one study ({@code /study/{id}}). */
@Controller
@Scope(value = "session")
@RequiredArgsConstructor
public class StudyController {

    private final NavBean navBean;

    @GetMapping("/study")
    public String toStudyList() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("study");
    }

    @GetMapping("/study/{id}")
    public String toStudy(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FocusForward.to("study/" + id);
    }
}
