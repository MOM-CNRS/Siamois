package fr.siamois.ui.bean.settings.administration;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.services.ApplicationMembersServiceInterface;
import fr.siamois.dto.entity.ApplicationMemberDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.dialog.administration.NewApplicationMemberDialogBean;
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
import java.util.List;

import static fr.siamois.utils.MessageUtils.displayWarnMessage;

@Slf4j
@Component
@Scope(value = "session")
@Getter
@Setter
public class ApplicationMembersListBean implements SettingsDatatableBean {

    private final transient ApplicationMembersServiceInterface applicationMembersService;
    private final NewApplicationMemberDialogBean newApplicationMemberDialogBean;
    private final LangBean langBean;

    private transient List<ApplicationMemberDTO> members;
    private transient List<ApplicationMemberDTO> refMembers;
    private String searchInput;

    public ApplicationMembersListBean(ApplicationMembersServiceInterface applicationMembersService,
                                      NewApplicationMemberDialogBean newApplicationMemberDialogBean,
                                      LangBean langBean) {
        this.applicationMembersService = applicationMembersService;
        this.newApplicationMemberDialogBean = newApplicationMemberDialogBean;
        this.langBean = langBean;
    }

    /** Loads the application's user accounts and resets the search filter. */
    public void init() {
        refMembers = new ArrayList<>(applicationMembersService.findMembers());
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
            for (ApplicationMemberDTO member : refMembers) {
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
        List<ProfileDTO> all = applicationMembersService.findAvailableProfiles();
        if (query == null || query.isBlank()) {
            return all;
        }
        String q = query.trim().toLowerCase();
        return all.stream()
                .filter(p -> p.getName().toLowerCase().contains(q))
                .toList();
    }

    /** Opens the "add users" wizard dialog. */
    @Override
    public void add() {
        log.trace("Creating application member");
        newApplicationMemberDialogBean.init(langBean.msg("administrationSettings.userManagement.dialog.label"),
                langBean.msg("organisationSettings.managers.add"),
                this::processPerson);
        PrimeFaces.current().ajax().update("newApplicationMemberDialog");
        PrimeFaces.current().executeScript("PF('newApplicationMemberDialog').show();");
    }

    private Boolean processPerson(PersonRole saved) {
        try {
            ApplicationMemberDTO member = applicationMembersService.addMember(
                    saved.person(), new ArrayList<>(saved.profiles()));
            refMembers.add(member);
            filter();
            return true;
        } catch (Exception err) {
            displayWarnMessage(langBean, "administrationSettings.error.member", saved.person().getEmail());
            return false;
        }
    }

    /** Clears the bean's state between sessions/logins. */
    @EventListener(LoginEvent.class)
    public void reset() {
        members = null;
        refMembers = null;
        searchInput = null;
    }

}