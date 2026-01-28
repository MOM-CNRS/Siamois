package fr.siamois.ui.bean.settings.institution;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.institution.PersonRole;
import fr.siamois.ui.bean.dialog.institution.UserDialogBean;
import fr.siamois.ui.bean.settings.SettingsDatatableBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static fr.siamois.utils.MessageUtils.displayInfoMessage;
import static fr.siamois.utils.MessageUtils.displayWarnMessage;

@Slf4j
@Component
@Scope(value = "session")
@Getter
@Setter
public class InstitutionManagerListBean implements SettingsDatatableBean {

    private final transient InstitutionService institutionService;
    private final transient PersonService personService;
    private final UserDialogBean userDialogBean;
    private final LangBean langBean;
    private final transient PendingPersonService pendingPersonService;
    private final SessionSettingsBean sessionSettingsBean;
    private Institution institution;
    private transient Map<Person, String> roles;

    private transient Set<Person> members;
    private transient Set<Person> refMembers;
    private String searchInput;

    public InstitutionManagerListBean(InstitutionService institutionService,
                                      PersonService personService,
                                      UserDialogBean userDialogBean,
                                      LangBean langBean,
                                      PendingPersonService pendingPersonService,
                                      SessionSettingsBean sessionSettingsBean) {
        this.institutionService = institutionService;
        this.personService = personService;
        this.userDialogBean = userDialogBean;
        this.langBean = langBean;
        this.pendingPersonService = pendingPersonService;
        this.sessionSettingsBean = sessionSettingsBean;
    }

    public void init(Institution institution) {
        this.institution = institution;
        refMembers = new HashSet<>(institutionService.findManagersOf(institution));
        roles = new HashMap<>();
        members = new HashSet<>(refMembers);
    }

    @Override
    public void filter() {
        log.trace("Filtering values with text: {}", searchInput);
        if (searchInput == null || searchInput.isEmpty()) {
            members = new HashSet<>(refMembers);
        } else {
            members = new HashSet<>();
            for (Person person : refMembers) {
                if (person.displayName().toLowerCase().contains(searchInput.toLowerCase())) {
                    members.add(person);
                }
            }
            for (Person person : refMembers) {
                if (person.getEmail().toLowerCase().contains(searchInput.toLowerCase())) {
                    members.add(person);
                }
            }
        }
    }

    @Override
    public void add() {
        log.trace("Creating manager");
        userDialogBean.init(langBean.msg("organisationSettings.managers.dialog.label"),
                langBean.msg("organisationSettings.managers.add"),
                institution,
                this::processPerson);
        PrimeFaces.current().ajax().update("newMemberDialog");
        PrimeFaces.current().executeScript("PF('newMemberDialog').show();");
    }

    private Boolean addPersonToInstitution(PersonRole saved) {
        try {
            if (institutionService.addToManagers(institution, saved.person())) {
                displayInfoMessage(langBean, "organisationSettings.action.addUserToManager", saved.person().getUsername());
                return true;
            } else {
                displayWarnMessage(langBean, "organisationSettings.error.manager", saved.person().getEmail(), institution.getName());
                PrimeFaces.current().executeScript("PF('newMemberDialog').showError();");
            }
            return false;
        } catch(Exception err) {
            displayWarnMessage(langBean, "organisationSettings.error.manager", saved.person().getEmail(), institution.getName());
            PrimeFaces.current().executeScript("PF('newMemberDialog').showError();");
            return false;
        }

    }

    private Boolean processPerson(PersonRole saved) {
        Boolean processed = addPersonToInstitution(saved);
        refMembers.add(saved.person());
        members.add(saved.person());
        return processed;
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        institution = null;
        members = null;
        refMembers = null;
        roles = null;
        searchInput = null;
    }

}
