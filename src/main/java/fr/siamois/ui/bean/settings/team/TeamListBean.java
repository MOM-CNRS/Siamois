package fr.siamois.ui.bean.settings.team;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.dialog.institution.UserDialogBean;
import fr.siamois.ui.bean.settings.SettingsDatatableBean;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class TeamListBean implements SettingsDatatableBean {

    private final transient ActionUnitService actionUnitService;
    private final TeamMembersBean teamMembersBean;
    private final UserDialogBean userDialogBean;
    private InstitutionDTO institution;
    private final NavBean navBean;

    private final transient InstitutionService institutionService;
    private String searchInput;

    private Set<ActionUnitDTO> actionUnits;
    private List<ActionUnitDTO> filteredActionUnits;

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
            for (ActionUnitDTO actionUnit : actionUnits) {
                if (actionUnit.getName().toLowerCase().contains(searchInput.toLowerCase())) {
                    filteredActionUnits.add(actionUnit);
                }
            }
        }
    }

    public int numberOfMemberInActionUnit(ActionUnitDTO actionUnit) {
        return institutionService.findMembersOf(actionUnit).size();
    }

    public void reset() {
        this.institution = null;
        this.searchInput = null;
        this.actionUnits = null;
        this.filteredActionUnits = null;
    }

    public void init(InstitutionDTO institution) {
        reset();
        this.institution = institution;
        this.actionUnits = actionUnitService.findAllByInstitution(institution);

        this.filteredActionUnits = new ArrayList<>(actionUnits);
    }

    public String manageTeamMember(ActionUnitDTO actionUnit) {
        teamMembersBean.init(actionUnit);
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        return "/pages/settings/team/manageTeamMember.xhtml?faces-redirect=true";
    }

    public String backToTeamList() {
        teamMembersBean.reset();
        return "/pages/settings/team/teamList.xhtml?faces-redirect=true";
    }

}
