package fr.siamois.ui.bean.settings.team;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.team.TeamMemberRelation;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.ui.bean.dialog.institution.UserDialogBean;
import fr.siamois.ui.bean.settings.SettingsDatatableBean;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Component
@SessionScoped
public class TeamListBean implements SettingsDatatableBean {

    private final transient ActionUnitService actionUnitService;
    private final TeamMembersBean teamMembersBean;
    private final UserDialogBean userDialogBean;
    private Institution institution;

    private final transient InstitutionService institutionService;
    private String searchInput;

    private Set<ActionUnit> actionUnits;
    private List<ActionUnit> filteredActionUnits;

    public TeamListBean(InstitutionService institutionService, ActionUnitService actionUnitService, TeamMembersBean teamMembersBean, UserDialogBean userDialogBean) {
        this.institutionService = institutionService;
        this.actionUnitService = actionUnitService;
        this.teamMembersBean = teamMembersBean;
        this.userDialogBean = userDialogBean;
    }

    @Override
    public void add() {
        throw new UnsupportedOperationException("Adding action units is not supported in this context.");
    }

    @Override
    public void filter() {
        if (searchInput == null || searchInput.isEmpty()) {
            filteredActionUnits = new ArrayList<>(actionUnits);
        } else {
            filteredActionUnits = new ArrayList<>();
            for (ActionUnit actionUnit : actionUnits) {
                if (actionUnit.getName().toLowerCase().contains(searchInput.toLowerCase())) {
                    filteredActionUnits.add(actionUnit);
                }
            }
        }
    }

    public int numberOfMemberInActionUnit(ActionUnit actionUnit) {
        return institutionService.findMembersOf(actionUnit).size();
    }

    public void reset() {
        this.institution = null;
        this.searchInput = null;
        this.actionUnits = null;
        this.filteredActionUnits = null;
    }

    public void init(Institution institution) {
        reset();
        this.institution = institution;
        this.actionUnits = actionUnitService.findAllByInstitution(institution);
        this.filteredActionUnits = new ArrayList<>(actionUnits);
    }

    public String manageTeamMember(ActionUnit actionUnit) {
        teamMembersBean.init(actionUnit);
        return "/pages/settings/team/manageTeamMember.xhtml?faces-redirect=true";
    }

    public String backToTeamList() {
        teamMembersBean.reset();
        return "/pages/settings/team/teamList.xhtml?faces-redirect=true";
    }

}
