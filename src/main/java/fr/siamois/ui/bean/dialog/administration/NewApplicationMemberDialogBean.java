package fr.siamois.ui.bean.dialog.administration;

import fr.siamois.domain.services.ApplicationMembersServiceInterface;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.ApplicationMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.dialog.AbstractNewMemberDialogBean;
import fr.siamois.ui.bean.dialog.institution.ProcessPerson;
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
 * Backs the "Ajouter des utilisateurs" wizard dialog for the application's own user management.
 */
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
@Setter
public class NewApplicationMemberDialogBean extends AbstractNewMemberDialogBean {

    private final transient ApplicationMembersServiceInterface applicationMembersService;

    public NewApplicationMemberDialogBean(PersonService personService,
                                           ApplicationMembersServiceInterface applicationMembersService,
                                           LangBean langBean) {
        super(personService, langBean);
        this.applicationMembersService = applicationMembersService;
    }

    /**
     * Resets and opens the wizard.
     *
     * @param title         the dialog title
     * @param buttonLabel   the label of the primary action button on the main step
     * @param processPerson callback invoked once per member on final submission
     */
    public void init(String title, String buttonLabel, ProcessPerson processPerson) {
        reset();
        applyCommonInit(title, buttonLabel, processPerson);
    }

    @Override
    protected String getDialogWidgetVar() {
        return "newApplicationMemberDialog";
    }

    @Override
    protected List<ProfileDTO> loadAvailableProfiles() {
        return applicationMembersService.findAvailableProfiles();
    }

    @Override
    public List<PersonDTO> completeMember(String query) {
        searchQuery = query;
        Set<Long> alreadyMemberIds = new HashSet<>();
        for (ApplicationMemberDTO member : applicationMembersService.findMembers()) {
            alreadyMemberIds.add(member.getPerson().getId());
        }
        List<PersonDTO> result = new ArrayList<>(personService.findClosestByUsernameOrEmail(query));
        result.removeIf(p -> alreadyMemberIds.contains(p.getId()));
        result.removeIf(p -> selectedMembers.stream().anyMatch(m -> m.getId().equals(p.getId())));
        return result;
    }

}
