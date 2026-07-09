package fr.siamois.ui.bean.settings.institution;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.OrganizationMembersServiceInterface;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.InstitutionMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.institution.NewOrganizationMemberDialogBean;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.siamois.utils.MessageUtils.displayWarnMessage;

@Slf4j
@Component
@Scope(value = "session")
@Getter
@Setter
@RequiredArgsConstructor
public class InstitutionMembersListBean implements SettingsDatatableBean {

    public static final String SETTINGS_ERROR_NOT_MANAGER = "organisationSettings.error.notManager";
    private final transient InstitutionService institutionService;
    private final transient PersonService personService;
    private final transient OrganizationMembersServiceInterface organizationMembersService;
    private final NewOrganizationMemberDialogBean newOrganizationMemberDialogBean;
    private final LangBean langBean;
    private final transient PendingPersonService pendingPersonService;
    private final SessionSettingsBean sessionSettingsBean;
    private final transient PersonProfileAssignmentService personProfileAssignmentService;
    private InstitutionDTO institution;
    private transient Map<Person, String> roles;

    private transient List<InstitutionMemberDTO> members;
    private transient List<InstitutionMemberDTO> refMembers;
    private transient List<ProfileDTO> availableProfiles;
    private transient Set<Long> pendingInvitationPersonIds;
    private String searchInput;

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
        availableProfiles = organizationMembersService.findAvailableProfiles(institution);
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
            for (InstitutionMemberDTO member : refMembers) {
                if (member.displayName().toLowerCase().contains(query)) {
                    members.add(member);
                }
            }
        }
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

    /** Removes the given member from the current institution. */
    public void removeMember(InstitutionMemberDTO member) {
        log.trace("Removing institution member {}", member.displayName());
        if (personProfileAssignmentService.isNotOrganisationManager(member.getCreatedByInstitution() ,sessionSettingsBean.getAuthenticatedUser())) {
            displayWarnMessage(langBean, SETTINGS_ERROR_NOT_MANAGER);
            return;
        }
        organizationMembersService.removeMemberFromInstitution(institution, member);
        refMembers.remove(member);
        filter();
    }

    /** Assigns the newly checked profile to the given member. */
    public void onProfileSelect(SelectEvent<ProfileDTO> event) {
        InstitutionMemberDTO member = (InstitutionMemberDTO) event.getComponent().getAttributes().get("member");
        if (personProfileAssignmentService.isNotOrganisationManager(member.getCreatedByInstitution(), sessionSettingsBean.getAuthenticatedUser())) {
            displayWarnMessage(langBean, SETTINGS_ERROR_NOT_MANAGER);
            return;
        }
        organizationMembersService.addProfileToMember(institution, member, event.getObject());
    }

    /** Unassigns the newly unchecked profile from the given member. */
    public void onProfileUnselect(UnselectEvent<ProfileDTO> event) {
        InstitutionMemberDTO member = (InstitutionMemberDTO) event.getComponent().getAttributes().get("member");
        if (personProfileAssignmentService.isNotOrganisationManager(member.getInstitution(), sessionSettingsBean.getAuthenticatedUser())) {
            displayWarnMessage(langBean, SETTINGS_ERROR_NOT_MANAGER);
            return;
        }
        ProfileDTO profile = event.getObject();
        boolean removed = organizationMembersService.removeProfileFromMember(institution, member, profile);
        if (!removed) {
            member.getProfiles().add(profile);
            displayWarnMessage(langBean, "organisationSettings.error.lastManager");
        } else if (member.getProfiles().isEmpty()) {
            // The service reassigns the base "member" profile when a member is left with none
            availableProfiles.stream()
                    .filter(p -> ProfileConstants.ORGANIZATION_MEMBER.equals(p.getCode()))
                    .findFirst()
                    .ifPresent(member.getProfiles()::add);
        }
    }

    private Boolean processPerson(PersonRole saved) {
        try {
            if (personProfileAssignmentService.isNotOrganisationManager(sessionSettingsBean.getSelectedInstitution(), sessionSettingsBean.getAuthenticatedUser())) {
                displayWarnMessage(langBean, SETTINGS_ERROR_NOT_MANAGER);
                return false;
            }
            InstitutionMemberDTO member = organizationMembersService.addMemberToInstitution(
                    institution, saved.person(), new ArrayList<>(saved.profiles()));
            refMembers.add(member);
            if (!saved.person().isEnabled() && pendingPersonService.hasPendingInvitation(saved.person().getId())) {
                pendingInvitationPersonIds.add(saved.person().getId());
            }
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
        availableProfiles = null;
        pendingInvitationPersonIds = null;
        searchInput = null;
    }

}
