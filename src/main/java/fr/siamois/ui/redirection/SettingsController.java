package fr.siamois.ui.redirection;

import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.InstitutionListSettingsBean;
import fr.siamois.ui.bean.settings.administration.ApplicationMembersListBean;
import fr.siamois.ui.bean.settings.project.ProjectListBean;
import jakarta.ws.rs.ForbiddenException;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Scope(value = "session")
public class SettingsController {

    private final NavBean navBean;
    private final InstitutionListSettingsBean institutionListSettingsBean;
    private final ApplicationMembersListBean applicationMembersListBean;
    private final SessionSettingsBean sessionSettingsBean;
    private final ProfilePermissionService profilePermissionService;
    private final ProjectListBean projectListBean;



    public SettingsController(NavBean navBean, InstitutionListSettingsBean institutionListSettingsBean,
                               ApplicationMembersListBean applicationMembersListBean,
                               SessionSettingsBean sessionSettingsBean,
                               ProfilePermissionService profilePermissionService,
                               ProjectListBean projectListBean) {
        this.navBean = navBean;
        this.institutionListSettingsBean = institutionListSettingsBean;
        this.applicationMembersListBean = applicationMembersListBean;
        this.sessionSettingsBean = sessionSettingsBean;
        this.profilePermissionService = profilePermissionService;
        this.projectListBean = projectListBean;
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
        if (!profilePermissionService.canViewInstitutionData(sessionSettingsBean.getUserInfo().getUser(), sessionSettingsBean.getSelectedInstitution())) {
            throw new ForbiddenException();
        }
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        institutionListSettingsBean.init();
        return "forward:/pages/settings/institutionListSettings.xhtml";
    }

    @GetMapping("/settings/project")
    public String goToProjectsSettings() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        projectListBean.init();
        return "forward:/pages/settings/project/projectList.xhtml";
    }

    @GetMapping("/settings/administration")
    public String goToUserManagementSettings() {
        if (!profilePermissionService.hasInstancePermission(
                sessionSettingsBean.getUserInfo().getUser(), PermissionConstants.INSTANCE_MANAGE_SETTINGS)) {
            throw new ForbiddenException();
        }
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        applicationMembersListBean.init();
        return "forward:/pages/settings/administration/userManagementSettings.xhtml";
    }

    @GetMapping("/dashboard")
    public String goToDashboard() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return "forward:/flow.xhtml";
    }

    /**
     * Redirect /focus/{mainToken}?s={secondaryToken} to JSF view
     */
    @GetMapping("/focus/{mainToken}")
    public String goToFocus(@PathVariable("mainToken") String mainToken,
                            @RequestParam(value = "s", required = false) String secondaryToken) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return "forward:/pages/focus.xhtml?main=" + mainToken
                + (secondaryToken != null ? "&s=" + secondaryToken : "");
    }


}
