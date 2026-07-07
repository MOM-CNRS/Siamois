package fr.siamois.ui.bean.settings.team;

import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.institution.PersonRole;
import fr.siamois.ui.bean.dialog.institution.UserDialogBean;
import fr.siamois.ui.bean.settings.SettingsDatatableBean;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class TeamMembersBean implements SettingsDatatableBean {

    private final transient InstitutionService institutionService;
    private final UserDialogBean userDialogBean;
    private final transient PersonService personService;
    private final transient PendingPersonService pendingPersonService;
    private final SessionSettingsBean sessionSettingsBean;
    private final RedirectBean redirectBean;
    private final LangBean langBean;
    private ActionUnitDTO actionUnit;

    private String searchInput;

    private Set<PersonDTO> members;
    private List<PersonDTO> filteredMembers;

    public void reset() {
        this.actionUnit = null;
        this.members = null;
        this.filteredMembers = null;
    }

    public void init(ActionUnitDTO actionUnit) {
        this.actionUnit = actionUnit;
        this.members = institutionService.findMembersOf(actionUnit);
        this.filteredMembers = new ArrayList<>(members);
    }

    @Override
    public void add() {
        userDialogBean.init("Ajouter des membres",
                langBean.msg("organisationSettings.managers.add"),
                actionUnit.getCreatedByInstitution(),
                true,
                this::processPerson);
        PrimeFaces.current().ajax().update("userDialogBeanForm:newMemberDialog");
        PrimeFaces.current().executeScript("PF('newMemberDialog').show();");
    }

    private void addPersonToActionunit(PersonRole saved) {
        if (institutionService.addPersonAsMemberOfActionUnit(actionUnit, saved.person())) {
            log.debug("Added person to action unit");
        } else {
            log.debug("Person was not added to action unit, maybe already exists");
        }
    }

    private Boolean processPerson(PersonRole saved) {
        addPersonToActionunit(saved);
        members.add(saved.person());
        filteredMembers.add(saved.person());
        return true;
    }

    @Override
    public void filter() {
        if (searchInput == null || searchInput.isEmpty()) {
            filteredMembers = new ArrayList<>(members);
        } else {
            filteredMembers = members.stream()
                    .filter(member -> member.getName().toLowerCase().contains(searchInput.toLowerCase()))
                    .toList();
        }
    }

    public void redirectToActionUnit() {
        redirectBean.redirectTo(String.format("/focus/action-unit/%s", actionUnit.getId()));
    }
}
