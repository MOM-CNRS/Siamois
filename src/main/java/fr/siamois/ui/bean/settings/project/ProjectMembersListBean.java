package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.domain.services.ProjectMembersServiceInterface;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.dto.entity.ProjectMemberDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.institution.PersonRole;
import fr.siamois.ui.bean.dialog.project.NewProjectMemberDialogBean;
import fr.siamois.ui.bean.settings.SettingsDatatableBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.event.SelectEvent;
import org.primefaces.event.UnselectEvent;
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
public class ProjectMembersListBean implements SettingsDatatableBean {

    private final transient ProjectMembersServiceInterface projectMembersService;
    private final NewProjectMemberDialogBean newProjectMemberDialogBean;
    private final LangBean langBean;
    private final transient PersonProfileAssignmentService personProfileAssignmentService;
    private final SessionSettingsBean sessionSettingsBean;

    private ActionUnitDTO project;

    private transient List<ProjectMemberDTO> members;
    private transient List<ProjectMemberDTO> refMembers;
    private transient List<ProfileDTO> availableProfiles;
    private String searchInput;

    public ProjectMembersListBean(ProjectMembersServiceInterface projectMembersService,
                                  NewProjectMemberDialogBean newProjectMemberDialogBean,
                                  LangBean langBean, PersonProfileAssignmentService personProfileAssignmentService, SessionSettingsBean sessionSettingsBean) {
        this.projectMembersService = projectMembersService;
        this.newProjectMemberDialogBean = newProjectMemberDialogBean;
        this.langBean = langBean;
        this.personProfileAssignmentService = personProfileAssignmentService;
        this.sessionSettingsBean = sessionSettingsBean;
    }

    /**
     * Loads the members of the given project and resets the search filter.
     *
     * @param project the project whose members page is being displayed
     */
    public void init(ActionUnitDTO project) {
        this.project = project;
        refMembers = new ArrayList<>(projectMembersService.findMembersOf(project));
        members = new ArrayList<>(refMembers);
        availableProfiles = projectMembersService.findAvailableProfiles(project);
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
            for (ProjectMemberDTO member : refMembers) {
                if (member.displayName().toLowerCase().contains(query)) {
                    members.add(member);
                }
            }
        }
    }

    /** Opens the "add members" wizard dialog for the current project. */
    @Override
    public void add() {
        log.trace("Creating project member");
        newProjectMemberDialogBean.init(langBean.msg("projectSettings.members.dialog.label"),
                langBean.msg("organisationSettings.managers.add"),
                project,
                this::processPerson);
        PrimeFaces.current().ajax().update("newProjectMemberDialog");
        PrimeFaces.current().executeScript("PF('newProjectMemberDialog').show();");
    }

    /** Removes the given member from the current project. */
    public void removeMember(ProjectMemberDTO member) {
        log.trace("Removing project member {}", member.displayName());
        projectMembersService.removeMemberFromProject(project, member);
        refMembers.remove(member);
        filter();
    }

    /** Assigns the newly checked profile to the given member. */
    public void onProfileSelect(SelectEvent<ProfileDTO> event) {
        ProjectMemberDTO member = (ProjectMemberDTO) event.getComponent().getAttributes().get("member");
        projectMembersService.addProfileToMember(project, member, event.getObject());
    }

    /** Unassigns the newly unchecked profile from the given member. */
    public void onProfileUnselect(UnselectEvent<ProfileDTO> event) {
        ProjectMemberDTO member = (ProjectMemberDTO) event.getComponent().getAttributes().get("member");
        ProfileDTO profile = event.getObject();
        boolean removed = projectMembersService.removeProfileFromMember(project, member, profile);
        if (!removed) {
            member.getProfiles().add(profile);
            displayWarnMessage(langBean, "projectSettings.error.lastManager");
        } else if (member.getProfiles().isEmpty()) {
            // The service reassigns the base "member" profile when a member is left with none
            availableProfiles.stream()
                    .filter(p -> ProfileConstants.PROJECT_MEMBER.equals(p.getCode()))
                    .findFirst()
                    .ifPresent(member.getProfiles()::add);
        }
    }

    private Boolean processPerson(PersonRole saved) {
        try {
            if (personProfileAssignmentService.isNotProjectManager(project, sessionSettingsBean.getAuthenticatedUser())) {
                displayWarnMessage(langBean, "projectSettings.error.notManager");
                return false;
            }
            ProjectMemberDTO member = projectMembersService.addMemberToProject(
                    project, saved.person(), new ArrayList<>(saved.profiles()));
            refMembers.add(member);
            filter();
            return true;
        } catch (Exception err) {
            displayWarnMessage(langBean, "projectSettings.error.member", saved.person().getEmail(), project.getName());
            return false;
        }
    }

    /** Clears the bean's state between sessions/logins. */
    @EventListener(LoginEvent.class)
    public void reset() {
        project = null;
        members = null;
        refMembers = null;
        availableProfiles = null;
        searchInput = null;
    }

}