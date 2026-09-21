package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Controller
@Scope(value = "session")
public class ActionUnitController {

    private static final String FOCUS_FORWARD_PREFIX = "forward:/pages/focus.xhtml?main=";
    private final NavBean navBean;

    public ActionUnitController(NavBean navBean) {
        this.navBean = navBean;
    }

    /**
     * Forwards straight to /pages/focus.xhtml (plan §7/§8 phase 8) rather than flow.xhtml —
     * FocusViewBean.beforeInit() decodes this token and builds the panel itself via
     * PanelFactory, the same way FlowBean.redirectToDashboard()/SettingsController.goToFocus
     * already do for Home and the settings pages. flow.xhtml's own tab-stack rendering never
     * gained the React branch this migration needs (it duplicates panelContent.xhtml but has no
     * equivalent of focus.xhtml's own titlebar), so Project's root routes bypass it entirely now.
     */
    private static String focusToken(String resourceUri) {
        String path = resourceUri.startsWith("/") ? resourceUri.substring(1) : resourceUri;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(path.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/action-unit/{id}")
    public String toActionUnit(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FOCUS_FORWARD_PREFIX + focusToken("action-unit/" + id);
    }

    @GetMapping("/action-unit")
    public String toActionUnitList() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return FOCUS_FORWARD_PREFIX + focusToken("action-unit");
    }

    @GetMapping("/spatial-unit/{id}/action-unit/new")
    public String addActionUnit(@PathVariable Long id) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return "forward:/flow.xhtml";
    }

}
