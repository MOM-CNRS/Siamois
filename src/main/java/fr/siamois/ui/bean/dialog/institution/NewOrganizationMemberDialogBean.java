package fr.siamois.ui.bean.dialog.institution;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.settings.institution.InstitutionMembersListBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.PrimeFaces;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;

/**
 * Backs the "Ajouter des membres" wizard dialog for an institution: search &amp; select existing
 * users and profiles through multi-select autocompletes, optionally invite new users by e-mail,
 * then submit everything at once — newly invited users are created only on final submission,
 * together with the already-existing ones.
 * <p>
 * A not-yet-created invitee is represented as a regular {@link PersonDTO} carrying a negative id
 * (the same "not persisted yet" convention used by {@link PersonService#createPerson}), so it can
 * live directly inside the multi-select member autocomplete like any other chip.
 */
@Slf4j
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
@Setter
public class NewOrganizationMemberDialogBean implements Serializable {

    private final transient PersonService personService;
    private final transient InstitutionService institutionService;
    private final LangBean langBean;

    private InstitutionDTO institution;
    private transient ProcessPerson processPerson;
    private String title;
    private String buttonLabel;

    private WizardStep step = WizardStep.MAIN;

    // Members multi-autocomplete
    private String searchQuery;
    private transient List<PersonDTO> selectedMembers = new ArrayList<>();
    private transient Map<Long, String> draftPasswords = new HashMap<>();
    private long nextDraftId = -1;

    // Profiles multi-autocomplete (applied to every member added in this batch)
    private transient List<ProfileDTO> selectedProfiles = new ArrayList<>();

    // Invite sub-step
    private String inviteEmail;
    private String inviteFirstName;
    private String inviteLastName;
    private String inviteUsername;
    private String invitePassword;
    private String invitePasswordConfirm;

    public NewOrganizationMemberDialogBean(PersonService personService, InstitutionService institutionService, LangBean langBean) {
        this.personService = personService;
        this.institutionService = institutionService;
        this.langBean = langBean;
    }

    public void init(String title, String buttonLabel, InstitutionDTO institution, ProcessPerson processPerson) {
        reset();
        this.title = title;
        this.buttonLabel = buttonLabel;
        this.institution = institution;
        this.processPerson = processPerson;
        PrimeFaces.current().ajax().update("newOrganizationMemberDialog");
    }

    @EventListener(LoginEvent.class)
    public final void reset() {
        institution = null;
        title = null;
        buttonLabel = null;
        processPerson = null;
        step = WizardStep.MAIN;
        searchQuery = null;
        selectedMembers = new ArrayList<>();
        draftPasswords = new HashMap<>();
        nextDraftId = -1;
        selectedProfiles = new ArrayList<>();
        clearInviteFields();
    }

    private void clearInviteFields() {
        inviteEmail = null;
        inviteFirstName = null;
        inviteLastName = null;
        inviteUsername = null;
        invitePassword = null;
        invitePasswordConfirm = null;
    }

    public void exit() {
        reset();
        PrimeFaces.current().executeScript("PF('newOrganizationMemberDialog').hide();");
    }

    public boolean isStepMain() {
        return step == WizardStep.MAIN;
    }

    public boolean isStepInvite() {
        return step == WizardStep.INVITE;
    }

    private static boolean isDraft(PersonDTO person) {
        return person.getId() != null && person.getId() < 0;
    }

    /** Autocomplete source for the members field — excludes already-selected and already-member persons. */
    public List<PersonDTO> completeMember(String query) {
        searchQuery = query;
        List<PersonDTO> result = new ArrayList<>(personService.findClosestByUsernameOrEmail(query));
        result.removeIf(p -> institutionService.personIsInInstitution(p, institution));
        result.removeIf(p -> selectedMembers.stream().anyMatch(m -> m.getId().equals(p.getId())));
        return result;
    }

    /** Mock autocomplete source for the profiles field, shared with the members datatable editor. */
    public List<ProfileDTO> completeProfile(String query) {
        List<ProfileDTO> all = InstitutionMembersListBean.availableProfiles();
        if (query == null || query.isBlank()) {
            return all;
        }
        String q = query.trim().toLowerCase();
        return all.stream().filter(p -> p.getName().toLowerCase().contains(q)).toList();
    }

    public void goToInvite() {
        String q = searchQuery == null ? "" : searchQuery.trim();
        if (q.contains("@")) {
            inviteEmail = q;
            inviteFirstName = "";
            inviteLastName = "";
            inviteUsername = usernameFromEmail(q);
        } else {
            inviteEmail = "";
            inviteFirstName = "";
            inviteLastName = q;
            inviteUsername = "";
        }
        invitePassword = null;
        invitePasswordConfirm = null;
        step = WizardStep.INVITE;
    }

    public void back() {
        clearInviteFields();
        step = WizardStep.MAIN;
    }

    public void primaryAction() {
        if (step == WizardStep.INVITE) {
            confirmInvite();
        } else if (!selectedMembers.isEmpty()) {
            confirmWizard();
        }
    }

    private void confirmInvite() {
        if (!inviteFieldsAreValid()) {
            return;
        }
        PersonDTO draft = new PersonDTO();
        draft.setId(nextDraftId--);
        draft.setName(inviteFirstName.trim());
        draft.setLastname(inviteLastName.trim());
        draft.setEmail(inviteEmail.trim());
        draft.setUsername(inviteUsername.trim());
        draftPasswords.put(draft.getId(), invitePassword);
        selectedMembers.add(draft);

        searchQuery = null;
        clearInviteFields();
        step = WizardStep.MAIN;
    }

    private boolean inviteFieldsAreValid() {
        if (StringUtils.isBlank(inviteEmail) || StringUtils.isBlank(inviteFirstName)
                || StringUtils.isBlank(inviteLastName) || StringUtils.isBlank(inviteUsername)
                || StringUtils.isBlank(invitePassword) || StringUtils.isBlank(invitePasswordConfirm)) {
            displayErrorMessage(langBean, "userDialog.error.fields");
            return false;
        }
        if (!isValidEmail(inviteEmail.trim())) {
            displayErrorMessage(langBean, "userDialog.error.email");
            return false;
        }
        if (!isValidUsername(inviteUsername.trim())) {
            displayErrorMessage(langBean, "userDialog.error.username");
            return false;
        }
        if (invitePassword.length() < 8) {
            displayErrorMessage(langBean, "userDialog.error.password");
            return false;
        }
        if (!invitePassword.equals(invitePasswordConfirm)) {
            displayErrorMessage(langBean, "userDialog.error.password.match");
            return false;
        }
        return true;
    }

    private static boolean isValidEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@')) {
            return false;
        }
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        return dot > 0 && dot < domain.length() - 1 && email.indexOf(' ') < 0;
    }

    private static boolean isValidUsername(String username) {
        if (username.length() < 3 || username.length() > 30) {
            return false;
        }
        return username.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '.');
    }

    /**
     * Creates every newly-invited member and submits ALL selected members — the ones that already
     * existed and the ones just created — to the institution via the {@link ProcessPerson} callback.
     * Members that fail (creation error, or rejected by the callback) are kept so the user can retry.
     */
    private void confirmWizard() {
        List<PersonDTO> remaining = new ArrayList<>();
        int affected = 0;
        Set<ProfileDTO> profiles = new HashSet<>(selectedProfiles);

        for (PersonDTO candidate : selectedMembers) {
            PersonDTO person = isDraft(candidate) ? createInvitedPerson(candidate) : candidate;
            if (person == null) {
                remaining.add(candidate);
                continue;
            }
            Boolean added = processPerson.process(new PersonRole(person, null, profiles));
            if (Boolean.TRUE.equals(added)) {
                affected++;
            } else {
                remaining.add(candidate);
            }
        }

        selectedMembers = remaining;
        if (affected > 0 && selectedMembers.isEmpty()) {
            exit();
        }
    }

    private PersonDTO createInvitedPerson(PersonDTO draft) {
        String password = draftPasswords.remove(draft.getId());

        PersonDTO person = new PersonDTO();
        person.setName(draft.getName());
        person.setLastname(draft.getLastname());
        person.setEmail(draft.getEmail());
        person.setUsername(draft.getUsername());
        person.setPassToModify(true);

        try {
            return personService.createPerson(person, password);
        } catch (InvalidUsernameException e) {
            displayErrorMessage(langBean, "userDialog.error.username");
        } catch (EmailAlreadyExistException e) {
            displayErrorMessage(langBean, "userDialog.error.email.alreadyexists", draft.getEmail());
        } catch (InvalidEmailException e) {
            displayErrorMessage(langBean, "userDialog.error.email");
        } catch (UserAlreadyExistException e) {
            displayErrorMessage(langBean, "userDialog.error.username.alreadyexists", draft.getUsername());
        } catch (InvalidPasswordException e) {
            displayErrorMessage(langBean, "userDialog.error.password");
        } catch (InvalidNameException e) {
            displayErrorMessage(langBean, "userDialog.error.name");
        }
        return null;
    }

    private static String usernameFromEmail(String email) {
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String sanitized = local.replaceAll("[^a-zA-Z0-9.]", ".");
        return sanitized.isBlank() ? "user" : sanitized;
    }

    public String getPrimaryActionLabel() {
        if (step == WizardStep.INVITE) {
            return langBean.msg("newOrganizationMember.action.invite");

        }
        return selectedMembers.isEmpty()
                ? langBean.msg("newOrganizationMember.action.add")
                : langBean.msg("newOrganizationMember.action.addCount", selectedMembers.size());
    }

    public boolean isPrimaryActionEnabled() {
        return step == WizardStep.INVITE || !selectedMembers.isEmpty();
    }

    public enum WizardStep {
        MAIN,
        INVITE
    }

}