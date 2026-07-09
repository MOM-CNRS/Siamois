package fr.siamois.ui.bean.dialog.project;

import fr.siamois.domain.services.ProjectMembersServiceInterface;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.dto.entity.ProjectMemberDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.dialog.AbstractNewMemberDialogBean;
import fr.siamois.ui.bean.dialog.institution.ProcessPerson;
import fr.siamois.ui.email.EmailManager;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Backs the "Ajouter des membres" wizard dialog for a project.
 */
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
@Setter
public class NewProjectMemberDialogBean extends AbstractNewMemberDialogBean {

    private final transient ProjectMembersServiceInterface projectMembersService;

    private ActionUnitDTO project;

    public NewProjectMemberDialogBean(PersonService personService,
                                       PendingPersonService pendingPersonService,
                                       EmailManager emailManager,
                                       ProjectMembersServiceInterface projectMembersService,
                                       LangBean langBean) {
        super(personService, pendingPersonService, emailManager, langBean);
        this.projectMembersService = projectMembersService;
    }

    /**
     * Resets and opens the wizard for the given project.
     *
     * @param title         the dialog title
     * @param buttonLabel   the label of the primary action button on the main step
     * @param project       the project members will be added to
     * @param processPerson callback invoked once per member on final submission
     */
    public void init(String title, String buttonLabel, ActionUnitDTO project, ProcessPerson processPerson) {
        reset();
        this.project = project;
        applyCommonInit(title, buttonLabel, processPerson);
    }

    @Override
    protected void resetScope() {
        project = null;
    }

    @Override
    protected String getDialogWidgetVar() {
        return "newProjectMemberDialog";
    }

    @Override
    protected List<ProfileDTO> loadAvailableProfiles() {
        return projectMembersService.findAvailableProfiles(project);
    }

    @Override
    protected String invitationMailSubject() {
        return langBean.msg("mail.invitation.project.subject", project.getName());
    }

    @Override
    protected String invitationMailBody(String invitationLink, String expirationDate) {
        return langBean.msg("mail.invitation.project.body", project.getName(), invitationLink, expirationDate);
    }

    @Override
    public List<PersonDTO> completeMember(String query) {
        searchQuery = query;
        Set<Long> alreadyMemberIds = new HashSet<>();
        for (ProjectMemberDTO member : projectMembersService.findMembersOf(project)) {
            alreadyMemberIds.add(member.getPerson().getId());
        }
        List<PersonDTO> result = new ArrayList<>(personService.findClosestByUsernameOrEmail(query));
        result.removeIf(p -> alreadyMemberIds.contains(p.getId()));
        result.removeIf(p -> selectedMembers.stream().anyMatch(m -> m.getId().equals(p.getId())));
        return result;
    }

}
