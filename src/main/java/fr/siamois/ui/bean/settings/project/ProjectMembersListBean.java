package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.services.ProjectMembersServiceInterface;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.dto.entity.ProjectMemberDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.dialog.institution.PersonRole;
import fr.siamois.ui.bean.dialog.project.NewProjectMemberDialogBean;
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
public class ProjectMembersListBean implements SettingsDatatableBean {

    private final transient ProjectMembersServiceInterface projectMembersService;
    private final NewProjectMemberDialogBean newProjectMemberDialogBean;
    private final LangBean langBean;

    private ActionUnitDTO project;

    private transient List<ProjectMemberDTO> members;
    private transient List<ProjectMemberDTO> refMembers;
    private String searchInput;

    public ProjectMembersListBean(ProjectMembersServiceInterface projectMembersService,
                                  NewProjectMemberDialogBean newProjectMemberDialogBean,
                                  LangBean langBean) {
        this.projectMembersService = projectMembersService;
        this.newProjectMemberDialogBean = newProjectMemberDialogBean;
        this.langBean = langBean;
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

    /**
     * Autocomplete source for the profile-chips editor in the members datatable.
     *
     * @param query the text currently typed in the profile field
     * @return the assignable profiles matching the query
     */
    public List<ProfileDTO> completeProfile(String query) {
        List<ProfileDTO> all = projectMembersService.findAvailableProfiles(project);
        if (query == null || query.isBlank()) {
            return all;
        }
        String q = query.trim().toLowerCase();
        return all.stream()
                .filter(p -> p.getName().toLowerCase().contains(q))
                .toList();
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

    private Boolean processPerson(PersonRole saved) {
        try {
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
        searchInput = null;
    }

}