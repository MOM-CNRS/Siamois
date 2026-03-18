package fr.siamois.ui.redirection;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.settings.InstitutionDetailsBean;
import fr.siamois.ui.bean.settings.InstitutionListSettingsBean;
import fr.siamois.ui.bean.settings.team.TeamListBean;
import fr.siamois.ui.bean.settings.team.TeamMembersBean;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Scope(value = "session")
@Controller
@RequiredArgsConstructor
public class TeamRedirectController {

    private final InstitutionListSettingsBean institutionListSettingsBean;
    private final ActionUnitService actionUnitService;
    private final TeamMembersBean teamMembersBean;
    private final InstitutionDetailsBean institutionDetailsBean;
    private final TeamListBean teamListBean;
    private final NavBean navBean;
    private final ConversionService conversionService;


    @GetMapping("/settings/organisation/actionunit/{actionUnitId}/members")
    public String redirectToTeam(@PathVariable Long actionUnitId) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        ActionUnit actionUnit = conversionService.convert(actionUnitService.findById(actionUnitId), ActionUnit.class);
        institutionListSettingsBean.init();
        institutionDetailsBean.setInstitution(actionUnit.getCreatedByInstitution());
        institutionDetailsBean.init();
        teamListBean.init(actionUnit.getCreatedByInstitution());
        teamMembersBean.init(actionUnit);
        return "forward:/pages/settings/team/manageTeamMember.xhtml";
    }

}
