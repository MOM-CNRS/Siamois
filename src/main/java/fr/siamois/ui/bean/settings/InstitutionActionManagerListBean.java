package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.team.ActionManagerRelation;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.institution.PersonRole;
import fr.siamois.ui.bean.dialog.institution.UserDialogBean;
import fr.siamois.utils.DateUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static fr.siamois.utils.MessageUtils.displayInfoMessage;
import static fr.siamois.utils.MessageUtils.displayWarnMessage;

@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class InstitutionActionManagerListBean implements SettingsDatatableBean {

    private final transient InstitutionService institutionService;
    private final UserDialogBean userDialogBean;
    private final LangBean langBean;
    private final transient PersonService personService;
    private final transient PendingPersonService pendingPersonService;
    private final SessionSettingsBean sessionSettingsBean;
    private Institution institution;
    private transient Set<ActionManagerRelation> refActionManagers;
    private transient List<ActionManagerRelation> filteredActionManagers;

    private String searchInput;

    public InstitutionActionManagerListBean(InstitutionService institutionService, UserDialogBean userDialogBean, LangBean langBean, PersonService personService, PendingPersonService pendingPersonService, SessionSettingsBean sessionSettingsBean) {
        this.institutionService = institutionService;
        this.userDialogBean = userDialogBean;
        this.langBean = langBean;
        this.personService = personService;
        this.pendingPersonService = pendingPersonService;
        this.sessionSettingsBean = sessionSettingsBean;
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        this.institution = null;
        this.refActionManagers = null;
        this.filteredActionManagers = null;
    }

    public void init(Institution institution) {
        this.institution = institution;
        this.refActionManagers = institutionService.findAllActionManagersOf(institution);
        this.filteredActionManagers = new ArrayList<>(refActionManagers);
    }

    @Override
    public void add() {
        userDialogBean.init(langBean.msg("organisationSettings.managers.dialog.label"),
                langBean.msg("organisationSettings.managers.add"),
                institution,
                this::processPerson);

        userDialogBean.getAlreadyExistingPersons().addAll(
                refActionManagers
                        .stream()
                        .map(ActionManagerRelation::getPerson)
                        .toList()
        );

        PrimeFaces.current().ajax().update("newMemberDialog");
        PrimeFaces.current().executeScript("PF('newMemberDialog').show();");
    }

    @Override
    public void filter() {
        if (searchInput == null || searchInput.isEmpty()) {
            filteredActionManagers = new ArrayList<>(refActionManagers);
        } else {
            filteredActionManagers.clear();

            for (ActionManagerRelation relation : refActionManagers) {
                if (relation.getPerson().displayName().toLowerCase().contains(searchInput.toLowerCase())) {
                    filteredActionManagers.add(relation);
                }
            }

            for (ActionManagerRelation relation : refActionManagers) {
                if (relation.getPerson().getEmail().toLowerCase().contains(searchInput.toLowerCase())) {
                    filteredActionManagers.add(relation);
                }
            }
        }
    }

    private void addToActionManagers(PersonRole saved) {
        if (institutionService.addPersonToActionManager(institution, saved.person())) {
            displayInfoMessage(langBean, "organisationSettings.action.addUserToManager", saved.person().getUsername());
        } else {
            displayWarnMessage(langBean, "organisationSettings.error.manager", saved.person().getEmail(), institution.getName());
            PrimeFaces.current().executeScript("PF('newMemberDialog').showError();");
        }
    }

    private Boolean processPerson(PersonRole personRole) {
        addToActionManagers(personRole);
        ActionManagerRelation relation = new ActionManagerRelation(institution, personRole.person());
        refActionManagers.add(relation);
        filteredActionManagers.add(relation);
        return true;
    }

    public String formatDate(ActionManagerRelation relation) {
        return DateUtils.formatOffsetDateTime(relation.getAddedAt());
    }

}
