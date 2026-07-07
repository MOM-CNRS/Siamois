package fr.siamois.ui.bean.settings.institution;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.auth.PendingPersonService;
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
import io.swagger.models.auth.In;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.siamois.utils.MessageUtils.displayInfoMessage;
import static fr.siamois.utils.MessageUtils.displayWarnMessage;

@Slf4j
@Component
@Scope(value = "session")
@Getter
@Setter
public class InstitutionMembersListBean implements SettingsDatatableBean {

    private static final List<ProfileDTO> AVAILABLE_PROFILES = buildAvailableProfiles();

    private final transient InstitutionService institutionService;
    private final transient PersonService personService;
    private final NewOrganizationMemberDialogBean newOrganizationMemberDialogBean;
    private final LangBean langBean;
    private final transient PendingPersonService pendingPersonService;
    private final SessionSettingsBean sessionSettingsBean;
    private InstitutionDTO institution;
    private transient Map<Person, String> roles;

    private transient List<InstitutionMemberDTO> members;
    private transient List<InstitutionMemberDTO> refMembers;
    private String searchInput;

    public InstitutionMembersListBean(InstitutionService institutionService,
                                      PersonService personService,
                                      NewOrganizationMemberDialogBean newOrganizationMemberDialogBean,
                                      LangBean langBean,
                                      PendingPersonService pendingPersonService,
                                      SessionSettingsBean sessionSettingsBean) {
        this.institutionService = institutionService;
        this.personService = personService;
        this.newOrganizationMemberDialogBean = newOrganizationMemberDialogBean;
        this.langBean = langBean;
        this.pendingPersonService = pendingPersonService;
        this.sessionSettingsBean = sessionSettingsBean;
    }

    public void init(InstitutionDTO institution) {
        this.institution = institution;
        // TODO : fetch members from institution
        InstitutionMemberDTO member = new InstitutionMemberDTO();
        PersonDTO person = new PersonDTO();
        person.setName("Chirac");
        person.setLastname("Jacques");
        person.setEmail("jacques.chirac@siamois.fr");
        person.setUsername("jacqouillelafripouille");
        person.setEnabled(true);
        member.setPerson(person);
        ProfileDTO profile = new ProfileDTO();
        profile.setName("Super Administrateur");
        ProfileDTO profile2 = new ProfileDTO();
        profile2.setName("Profil 2");
        member.setProfiles(new ArrayList<>(List.of(profile, profile2)));
        refMembers = new ArrayList<>(List.of(member));
        roles = new HashMap<>();
        members = new ArrayList<>(refMembers);
    }

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

    /** Mock autocomplete source for the profile-chips editor — filters the fixed in-memory catalog, no DB call. */
    public List<ProfileDTO> completeProfile(String query) {
        if (query == null || query.isBlank()) {
            return AVAILABLE_PROFILES;
        }
        String q = query.trim().toLowerCase();
        return AVAILABLE_PROFILES.stream()
                .filter(p -> p.getName().toLowerCase().contains(q))
                .toList();
    }

    /** Mock profile catalog, also used by {@link fr.siamois.ui.bean.dialog.institution.NewOrganizationMemberDialogBean}. */
    public static List<ProfileDTO> availableProfiles() {
        return AVAILABLE_PROFILES;
    }

    private static List<ProfileDTO> buildAvailableProfiles() {
        List<ProfileDTO> profiles = new ArrayList<>();
        for (String name : List.of("Super Administrateur", "Responsable scientifique", "Contributeur", "Profil 2", "Lecture seule")) {
            ProfileDTO profile = new ProfileDTO();
            profile.setName(name);
            profiles.add(profile);
        }
        return profiles;
    }

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

    private Boolean processPerson(PersonRole saved) {
        try {
            // todo persist member to institution
            InstitutionMemberDTO member = new InstitutionMemberDTO();
            member.setPerson(saved.person());
            member.setProfiles(new ArrayList<>(saved.profiles()));
            refMembers.add(member);
            members.add(member);
            return true;
        } catch (Exception err) {
            displayWarnMessage(langBean, "organisationSettings.error.manager", saved.person().getEmail(), institution.getName());
            return false;
        }
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        institution = null;
        members = null;
        refMembers = null;
        roles = null;
        searchInput = null;
    }

}
