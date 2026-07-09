package fr.siamois.ui.bean.settings.administration;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.services.ApplicationMembersServiceInterface;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.dto.entity.ApplicationMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.administration.NewApplicationMemberDialogBean;
import fr.siamois.ui.bean.dialog.institution.PersonRole;
import fr.siamois.ui.bean.settings.SettingsDatatableBean;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.event.SelectEvent;
import org.primefaces.event.UnselectEvent;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static fr.siamois.utils.MessageUtils.displayWarnMessage;

@Slf4j
@Component
@Scope(value = "session")
@Getter
@Setter
@RequiredArgsConstructor
public class ApplicationMembersListBean implements SettingsDatatableBean {

    private final transient ApplicationMembersServiceInterface applicationMembersService;
    private final NewApplicationMemberDialogBean newApplicationMemberDialogBean;
    private final LangBean langBean;
    private final SessionSettingsBean sessionSettingsBean;
    private final transient PersonProfileAssignmentService personProfileAssignmentService;
    private final transient PendingPersonService pendingPersonService;

    private transient List<ApplicationMemberDTO> members;
    private transient List<ApplicationMemberDTO> refMembers;
    private transient List<ProfileDTO> availableProfiles;
    private transient Set<Long> pendingInvitationPersonIds;
    private String searchInput;


    /** Loads the application's user accounts and resets the search filter. */
    public void init() {
        refMembers = new ArrayList<>(applicationMembersService.findMembers());
        members = new ArrayList<>(refMembers);
        availableProfiles = applicationMembersService.findAvailableProfiles();
        pendingInvitationPersonIds = new HashSet<>(pendingPersonService.findPersonIdsWithPendingInvitation(
                refMembers.stream().map(m -> m.getPerson().getId()).toList()));
    }

    /** @return true when the member's account is disabled and still waiting on its invitation. */
    public boolean hasPendingInvitation(PersonDTO person) {
        return !person.isEnabled()
                && pendingInvitationPersonIds != null
                && pendingInvitationPersonIds.contains(person.getId());
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

    /** Assigns the newly checked profile to the given member. */
    public void onProfileSelect(SelectEvent<ProfileDTO> event) {
        ApplicationMemberDTO member = (ApplicationMemberDTO) event.getComponent().getAttributes().get("member");
        if (personProfileAssignmentService.isNotSuperAdmin(sessionSettingsBean.getAuthenticatedUser())) {
            displayWarnMessage(langBean, "administrationSettings.error.notAdmin");
            return;
        }
        applicationMembersService.addProfileToMember(member, event.getObject());
    }

    /** Unassigns the newly unchecked profile from the given member. */
    public void onProfileUnselect(UnselectEvent<?> event) {
        ApplicationMemberDTO member = (ApplicationMemberDTO) event.getComponent().getAttributes().get("member");
        if (personProfileAssignmentService.isNotSuperAdmin(sessionSettingsBean.getAuthenticatedUser())) {
            displayWarnMessage(langBean, "administrationSettings.error.notAdmin");
            return;
        }
        ProfileDTO profile = (ProfileDTO) event.getObject();
        boolean removed = applicationMembersService.removeProfileFromMember(member, profile);
        if (!removed) {
            member.getProfiles().add(profile);
            displayWarnMessage(langBean, "administrationSettings.error.lastSuperAdmin");
        }
    }

    private Boolean processPerson(PersonRole saved) {
        try {
            ApplicationMemberDTO member = applicationMembersService.addMember(
                    saved.person(), new ArrayList<>(saved.profiles()));
            refMembers.add(member);
            if (!saved.person().isEnabled() && pendingPersonService.hasPendingInvitation(saved.person().getId())) {
                pendingInvitationPersonIds.add(saved.person().getId());
            }
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
        availableProfiles = null;
        pendingInvitationPersonIds = null;
        searchInput = null;
    }

}