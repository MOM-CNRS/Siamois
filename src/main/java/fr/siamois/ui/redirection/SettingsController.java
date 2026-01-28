package fr.siamois.ui.redirection;

import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.settings.InstitutionListSettingsBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Scope(value = "session")
public class SettingsController {

    private final NavBean navBean;
    private final InstitutionListSettingsBean institutionListSettingsBean;

    public SettingsController(NavBean navBean, InstitutionListSettingsBean institutionListSettingsBean) {
        this.navBean = navBean;
        this.institutionListSettingsBean = institutionListSettingsBean;
    }

    @GetMapping("/settings")
    public String goToSettings() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        return "forward:/pages/settings/profileSettings.xhtml";
    }

    @GetMapping("/settings/profile")
    public String goToSettingsByProfile() {
        return goToSettings();
    }

    @GetMapping("/settings/profile/thesaurus")
    public String goToThesaurusProfile() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        return "forward:/pages/settings/thesaurusSettings.xhtml";
    }

    @GetMapping("/settings/organisation")
    public String goToAdminInstitutionSettings() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        institutionListSettingsBean.init();
        return "forward:/pages/settings/institutionListSettings.xhtml";
    }

    @GetMapping("/dashboard")
    public String goToDashboard() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return "forward:/flow.xhtml";
    }


}
