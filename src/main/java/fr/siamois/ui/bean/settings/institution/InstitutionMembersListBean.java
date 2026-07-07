package fr.siamois.ui.bean.settings.institution;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.OrganizationMembersServiceInterface;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.InstitutionMemberDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.institution.NewOrganizationMemberDialogBean;
import fr.siamois.ui.bean.dialog.institution.PersonRole;
import fr.siamois.ui.bean.settings.SettingsDatatableBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.siamois.utils.MessageUtils.displayWarnMessage;

@Slf4j
@Component
@Scope(value = "session")
@Getter
@Setter
public class InstitutionMembersListBean implements SettingsDatatableBean {

    private final transient InstitutionService institutionService;
    private final transient PersonService personService;
    private final transient OrganizationMembersServiceInterface organizationMembersService;
    private final NewOrganizationMemberDialogBean newOrganizationMemberDialogBean;
    private final LangBean langBean;
    private final transient PendingPersonService pendingPersonService;
    private final SessionSettingsBean sessionSettingsBean;
    private InstitutionDTO institution;
    private transient Map<Person, String> roles;

    private transient List<InstitutionMemberDTO> members;
    private transient List<InstitutionMemberDTO> refMembers;
    private String searchInput;

    public InstitutionMembersListBean(InstitutionService institutionService,
                                      PersonService personService,
                                      OrganizationMembersServiceInterface organizationMembersService,
                                      NewOrganizationMemberDialogBean newOrganizationMemberDialogBean,
                                      LangBean langBean,
                                      PendingPersonService pendingPersonService,
                                      SessionSettingsBean sessionSettingsBean) {
        this.institutionService = institutionService;
        this.personService = personService;
        this.organizationMembersService = organizationMembersService;
        this.newOrganizationMemberDialogBean = newOrganizationMemberDialogBean;
        this.langBean = langBean;
        this.pendingPersonService = pendingPersonService;
        this.sessionSettingsBean = sessionSettingsBean;
    }

    /**
     * Loads the members of the given institution and resets the search filter.
     *
     * @param institution the institution whose members page is being displayed
     */
    public void init(InstitutionDTO institution) {
        this.institution = institution;
        refMembers = new ArrayList<>(organizationMembersService.findMembersOf(institution));
        roles = new HashMap<>();
        members = new ArrayList<>(refMembers);
    }

    /** Filters {@link #members} from {@link #refMembers} using {@link #searchInput}. */
    @Override
    public void filter() {
        log.trace("Filtering values with text: {}", searchInput);
        if (searchInput == null || searchInput.isEmpty()) {
            members = new ArrayList<>(refMembers);
        } else {
            String query = searchInput.toLowerCase();
            members = new ArrayList<>();
            for (InstitutionMemberDTO member : refMembers) {
                if (member.displayName().toLowerCase().contains(query)) {
                    members.add(member);
                }
            }
        }
    }

    /**
     * Autocomplete source for the profile-chips editor in the members datatable.
     *
     * @param query the text currently typed in the profile field
     * @return the assignable profiles matching the query
     */
    public List<ProfileDTO> completeProfile(String query) {
        List<ProfileDTO> all = organizationMembersService.findAvailableProfiles(institution);
        if (query == null || query.isBlank()) {
            return all;
        }
        String q = query.trim().toLowerCase();
        return all.stream()
                .filter(p -> p.getName().toLowerCase().contains(q))
                .toList();
    }

    /** Opens the "add members" wizard dialog for the current institution. */
    @Override
    public void add() {
        log.trace("Creating member");
        newOrganizationMemberDialogBean.init(langBean.msg("organisationSettings.managers.dialog.label"),
                langBean.msg("organisationSettings.managers.add"),
                institution,
                this::processPerson);
        PrimeFaces.current().ajax().update("newOrganizationMemberDialog");
        PrimeFaces.current().executeScript("PF('newOrganizationMemberDialog').show();");
    }

    private Boolean processPerson(PersonRole saved) {
        try {
            InstitutionMemberDTO member = organizationMembersService.addMemberToInstitution(
                    institution, saved.person(), new ArrayList<>(saved.profiles()));
            refMembers.add(member);
            filter();
            return true;
        } catch (Exception err) {
            displayWarnMessage(langBean, "organisationSettings.error.manager", saved.person().getEmail(), institution.getName());
            return false;
        }
    }

    /** Clears the bean's state between sessions/logins. */
    @EventListener(LoginEvent.class)
    public void reset() {
        institution = null;
        members = null;
        refMembers = null;
        roles = null;
        searchInput = null;
    }

}
