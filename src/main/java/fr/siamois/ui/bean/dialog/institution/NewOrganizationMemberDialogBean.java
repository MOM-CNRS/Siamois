package fr.siamois.ui.bean.dialog.institution;

import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.OrganizationMembersServiceInterface;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.dialog.AbstractNewMemberDialogBean;
import fr.siamois.ui.email.EmailManager;
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
                                            InstitutionService institutionService,
                                            OrganizationMembersServiceInterface organizationMembersService,
                                            LangBean langBean) {
        super(personService, pendingPersonService, emailManager, langBean);
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
    protected String invitationMailBody(String invitationLink, String expirationDate) {
        return langBean.msg("mail.invitation.body", institution.getName(), invitationLink, expirationDate);
    }

    @Override
    public List<PersonDTO> completeMember(String query) {
        searchQuery = query;
        List<PersonDTO> result = new ArrayList<>(personService.findClosestByUsernameOrEmail(query));
        result.removeIf(p -> institutionService.personIsInInstitution(p, institution));
        result.removeIf(p -> selectedMembers.stream().anyMatch(m -> m.getId().equals(p.getId())));
        return result;
    }

}
