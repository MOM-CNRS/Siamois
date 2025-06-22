package fr.siamois.ui.bean.settings.team;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.team.TeamMemberRelation;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.institution.UserDialogBean;
import fr.siamois.ui.bean.settings.SettingsDatatableBean;
import fr.siamois.utils.DateUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Getter
@Setter
@Component
@SessionScoped
public class TeamMembersBean implements SettingsDatatableBean {

    private final LabelBean labelBean;
    private final transient InstitutionService institutionService;
    private final UserDialogBean userDialogBean;
    private final transient PersonService personService;
    private final transient PendingPersonService pendingPersonService;
    private final SessionSettingsBean sessionSettingsBean;
    private final RedirectBean redirectBean;
    private final LangBean langBean;
    private ActionUnit actionUnit;

    private String searchInput;

    private Set<TeamMemberRelation> memberRelations;
    private List<TeamMemberRelation> filteredMemberRelations;

    public TeamMembersBean(LabelBean labelBean,
                           InstitutionService institutionService,
                           UserDialogBean userDialogBean,
                           PersonService personService,
                           PendingPersonService pendingPersonService,
                           SessionSettingsBean sessionSettingsBean,
                           RedirectBean redirectBean, LangBean langBean) {
        this.labelBean = labelBean;
        this.institutionService = institutionService;
        this.userDialogBean = userDialogBean;
        this.personService = personService;
        this.pendingPersonService = pendingPersonService;
        this.sessionSettingsBean = sessionSettingsBean;
        this.redirectBean = redirectBean;
        this.langBean = langBean;
    }

    public void reset() {
        this.actionUnit = null;
        this.memberRelations = null;
        this.filteredMemberRelations = null;
    }

    public void init(ActionUnit actionUnit) {
        this.actionUnit = actionUnit;
        this.memberRelations = institutionService.findRelationsOf(actionUnit);
        this.filteredMemberRelations = new ArrayList<>(memberRelations);
    }

    @Override
    public void add() {
        userDialogBean.init("Ajouter des membres",
                langBean.msg("organisationSettings.managers.add"),
                actionUnit.getCreatedByInstitution(),
                true,
                this::save);
        PrimeFaces.current().ajax().update("userDialogBeanForm:newMemberDialog");
        PrimeFaces.current().executeScript("PF('newMemberDialog').show();");
    }

    private void addPersonToActionunit(UserDialogBean.PersonRole saved) {
        if (institutionService.addPersonToActionUnit(actionUnit, saved.person(), saved.role())) {
            log.debug("Added person to action unit");
        } else {
            log.debug("Person was not added to action unit, maybe already exists");
        }
    }

    public void save() {
        List<UserDialogBean.PersonRole> result = userDialogBean.createOrSearchPersons();
        for (UserDialogBean.PersonRole saved : result) {
            addPersonToActionunit(saved);
            TeamMemberRelation relation = new TeamMemberRelation(actionUnit, saved.person());
            memberRelations.add(relation);
            filteredMemberRelations.add(relation);
        }
        userDialogBean.exit();
    }

    @Override
    public void filter() {
        if (searchInput == null || searchInput.isEmpty()) {
            filteredMemberRelations = new ArrayList<>(memberRelations);
        } else {
            filteredMemberRelations = memberRelations.stream()
                    .filter(relation -> relation.getPerson().getName().toLowerCase().contains(searchInput.toLowerCase()))
                    .toList();
        }
    }

    public String formatRole(TeamMemberRelation relation) {
        if (relation.getRole() == null) {
            return "";
        }
        return labelBean.findLabelOf(relation.getRole());
    }

    public String formatDate(TeamMemberRelation relation) {
        return DateUtils.formatOffsetDateTime(relation.getAddedAt());
    }

    public void redirectToActionUnit() {
        redirectBean.redirectTo(String.format("/actionunit/%s", actionUnit.getId()));
    }
}
