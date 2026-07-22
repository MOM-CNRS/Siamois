package fr.siamois.ui.bean.dialog.project;

import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.domain.services.ProjectMembersServiceInterface;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.*;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.AbstractNewMemberDialogBean;
import fr.siamois.ui.bean.dialog.institution.ProcessPerson;
import fr.siamois.ui.email.InvitationMailer;
import fr.siamois.ui.email.InvitationMessages;
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
                                       InvitationMailer invitationMailer,
                                       ProjectMembersServiceInterface projectMembersService,
                                       SessionSettingsBean sessionSettingsBean,
                                       LangBean langBean) {
        super(personService, pendingPersonService, invitationMailer, sessionSettingsBean, langBean);
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
        return InvitationMessages.projectSubject(langBean, project.getName());
    }

    @Override
    protected String invitationScopeName() {
        InstitutionDTO organisation = sessionSettingsBean.getSelectedInstitution();
        String organisationName = organisation == null ? null : organisation.getName();
        return InvitationMessages.projectScope(langBean, project.getName(), organisationName);
    }

    @Override
    protected String baseMemberProfileCode() {
        return ProfileConstants.PROJECT_MEMBER;
    }

    /**
     * Autocomplete source for the members field in the project scope: matches persons by username or
     * e-mail, then drops those already members of the project or already staged in the current batch.
     * As a side effect, remembers the typed query so {@link #goToInvite()} can prefill the invite form.
     *
     * @param query the text currently typed in the members field
     * @return the matching persons, excluding already-member and already-selected ones
     */
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
