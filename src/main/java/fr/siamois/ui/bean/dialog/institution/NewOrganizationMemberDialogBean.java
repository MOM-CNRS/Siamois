package fr.siamois.ui.bean.dialog.institution;

import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.OrganizationMembersServiceInterface;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.AbstractNewMemberDialogBean;
import fr.siamois.ui.email.EmailManager;
import fr.siamois.ui.email.InvitationEmailRenderer;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs the "Ajouter des membres" wizard dialog for an institution.
 */
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
@Setter
public class NewOrganizationMemberDialogBean extends AbstractNewMemberDialogBean {

    private final transient InstitutionService institutionService;
    private final transient OrganizationMembersServiceInterface organizationMembersService;

    private InstitutionDTO institution;

    public NewOrganizationMemberDialogBean(PersonService personService,
                                            PendingPersonService pendingPersonService,
                                            EmailManager emailManager,
                                            InvitationEmailRenderer invitationEmailRenderer,
                                            InstitutionService institutionService,
                                            OrganizationMembersServiceInterface organizationMembersService,
                                            SessionSettingsBean sessionSettingsBean,
                                            LangBean langBean) {
        super(personService, pendingPersonService, emailManager, invitationEmailRenderer, sessionSettingsBean, langBean);
        this.institutionService = institutionService;
        this.organizationMembersService = organizationMembersService;
    }

    /**
     * Resets and opens the wizard for the given institution.
     *
     * @param title         the dialog title
     * @param buttonLabel   the label of the primary action button on the main step
     * @param institution   the institution members will be added to
     * @param processPerson callback invoked once per member on final submission
     */
    public void init(String title, String buttonLabel, InstitutionDTO institution, ProcessPerson processPerson) {
        reset();
        this.institution = institution;
        applyCommonInit(title, buttonLabel, processPerson);
    }

    @Override
    protected void resetScope() {
        institution = null;
    }

    @Override
    protected String getDialogWidgetVar() {
        return "newOrganizationMemberDialog";
    }

    @Override
    protected List<ProfileDTO> loadAvailableProfiles() {
        return organizationMembersService.findAvailableProfiles(institution);
    }

    @Override
    protected String invitationMailSubject() {
        return langBean.msg("mail.invitation.subject", institution.getName());
    }

    @Override
    protected String invitationScopeName() {
        return langBean.msg("mail.invitation.scope.institution", institution.getName());
    }

    @Override
    protected String baseMemberProfileCode() {
        return ProfileConstants.ORGANIZATION_MEMBER;
    }

    /**
     * Autocomplete source for the members field in the institution scope: matches persons by username or
     * e-mail, then drops those already members of the institution or already staged in the current batch.
     * As a side effect, remembers the typed query so {@link #goToInvite()} can prefill the invite form.
     *
     * @param query the text currently typed in the members field
     * @return the matching persons, excluding already-member and already-selected ones
     */
    @Override
    public List<PersonDTO> completeMember(String query) {
        searchQuery = query;
        List<PersonDTO> result = new ArrayList<>(personService.findClosestByUsernameOrEmail(query));
        result.removeIf(p -> institutionService.personIsInInstitution(p, institution));
        result.removeIf(p -> selectedMembers.stream().anyMatch(m -> m.getId().equals(p.getId())));
        return result;
    }

}
