package fr.siamois.ui.bean.dialog;

import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.auth.*;
import fr.siamois.domain.services.auth.PendingPersonService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.institution.PersonRole;
import fr.siamois.ui.bean.dialog.institution.ProcessPerson;
import fr.siamois.ui.email.EmailManager;
import fr.siamois.ui.email.InvitationEmailRenderer;
import fr.siamois.utils.DateUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.PrimeFaces;
import org.springframework.context.event.EventListener;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;
import static fr.siamois.utils.MessageUtils.displayInfoMessage;

/**
 * Backs the "Ajouter des membres/utilisateurs" wizard dialog shared by the institution, project and
 * application member-management pages: search &amp; select existing users and profiles through
 * multi-select fields, optionally invite new users by e-mail, then submit everything at once —
 * newly invited users are created only on final submission, together with the already-existing ones.
 * <p>
 * A not-yet-created invitee is represented as a regular {@link PersonDTO} carrying a negative id
 * (the same "not persisted yet" convention used by {@link PersonService#createPerson}), so it can
 * live directly inside the multi-select member field like any other chip.
 * <p>
 * Subclasses only supply what differs by scope (institution / project / application-wide): the
 * dialog's widget var, the assignable profiles, and the already-member exclusion query.
 */
@Slf4j
@Getter
@Setter
public abstract class AbstractNewMemberDialogBean implements Serializable {

    protected final transient PersonService personService;
    protected final transient PendingPersonService pendingPersonService;
    protected final transient EmailManager emailManager;
    protected final transient InvitationEmailRenderer invitationEmailRenderer;
    protected final SessionSettingsBean sessionSettingsBean;
    protected final LangBean langBean;

    protected transient ProcessPerson processPerson;
    protected String title;
    protected String buttonLabel;

    protected WizardStep step = WizardStep.MAIN;

    // Members multi-select
    protected String searchQuery;
    protected transient List<PersonDTO> selectedMembers = new ArrayList<>();
    protected transient Map<Long, String> draftPasswords = new HashMap<>();
    protected long nextDraftId = -1;

    // Profiles multi-select (applied to every member added in this batch)
    protected transient List<ProfileDTO> selectedProfiles = new ArrayList<>();
    protected transient List<ProfileDTO> availableProfiles = new ArrayList<>();

    // Invite sub-step
    protected String inviteEmail;
    protected String inviteFirstName;
    protected String inviteLastName;
    protected String inviteUsername;
    protected String invitePassword;
    protected String invitePasswordConfirm;

    protected AbstractNewMemberDialogBean(PersonService personService,
                                          PendingPersonService pendingPersonService,
                                          EmailManager emailManager,
                                          InvitationEmailRenderer invitationEmailRenderer,
                                          SessionSettingsBean sessionSettingsBean,
                                          LangBean langBean) {
        this.personService = personService;
        this.pendingPersonService = pendingPersonService;
        this.emailManager = emailManager;
        this.invitationEmailRenderer = invitationEmailRenderer;
        this.sessionSettingsBean = sessionSettingsBean;
        this.langBean = langBean;
    }

    /** @return the PrimeFaces widget var of the dialog this bean backs, used to update/hide it. */
    protected abstract String getDialogWidgetVar();

    /**
     * Exposes the dialog's widget var/id so the shared dialog fragment can build its own component ids from it.
     *
     * @return the PrimeFaces widget var / id of the dialog this bean backs
     */
    public final String getDialogId() {
        return getDialogWidgetVar();
    }

    /** @return the profiles that can be assigned to a new member in this bean's scope. */
    protected abstract List<ProfileDTO> loadAvailableProfiles();

    /** @return the subject of the invitation e-mail sent to a member created without password. */
    protected abstract String invitationMailSubject();

    /**
     * @return the localised scope phrase shown in the invitation e-mail body, qualified by scope type so
     * the invitee knows what they are joining (e.g. "the project X", "the institution X", or "SIAMOIS").
     */
    protected abstract String invitationScopeName();

    /**
     * @return the code of the base "member" role implicitly granted to every invitee in this scope
     * (so the invitation e-mail can list it even when the inviter selected no extra profile), or
     * {@code null} when the scope grants no implicit membership role (application-wide management).
     */
    protected String baseMemberProfileCode() {
        return null;
    }

    /**
     * Autocomplete source for the members field — excludes already-selected and already-member persons.
     * As a side effect, remembers the query so {@link #goToInvite()} can prefill the invite form from it.
     *
     * @param query the text currently typed in the members field
     * @return matching persons, filtered
     */
    public abstract List<PersonDTO> completeMember(String query);

    /** Clears any scope field (institution, project, ...) held by a subclass. No-op by default. */
    protected void resetScope() {
        // overridden by subclasses that carry a scope field
    }

    /** Finishes {@code init(...)} once a subclass has reset and set its own scope field. */
    protected final void applyCommonInit(String title, String buttonLabel, ProcessPerson processPerson) {
        this.title = title;
        this.buttonLabel = buttonLabel;
        this.processPerson = processPerson;
        this.availableProfiles = loadAvailableProfiles();
        PrimeFaces.current().ajax().update(getDialogWidgetVar());
    }

    /**
     * Clears the whole wizard state — scope field, selected members and profiles, staged invite drafts,
     * the current step and the pending {@link ProcessPerson} callback. Also invoked automatically on every
     * {@link LoginEvent} so a fresh login never inherits a previous user's dialog state.
     */
    @EventListener(LoginEvent.class)
    public final void reset() {
        resetScope();
        title = null;
        buttonLabel = null;
        processPerson = null;
        step = WizardStep.MAIN;
        searchQuery = null;
        selectedMembers = new ArrayList<>();
        draftPasswords = new HashMap<>();
        nextDraftId = -1;
        selectedProfiles = new ArrayList<>();
        availableProfiles = new ArrayList<>();
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

    /** Resets the whole wizard state and closes the dialog client-side without submitting anything. */
    public void exit() {
        reset();
        PrimeFaces.current().executeScript("PF('" + getDialogWidgetVar() + "').hide();");
    }

    /**
     * Tells whether the wizard is currently on its first step, where existing members and profiles are selected.
     *
     * @return {@code true} when the wizard is on the member/profile selection step, {@code false} otherwise
     */
    public boolean isStepMain() {
        return step == WizardStep.MAIN;
    }

    /**
     * Tells whether the wizard is currently on the sub-step used to invite a brand-new user by e-mail.
     *
     * @return {@code true} when the wizard is on the "invite a new user" sub-step, {@code false} otherwise
     */
    public boolean isStepInvite() {
        return step == WizardStep.INVITE;
    }

    protected static boolean isDraft(PersonDTO person) {
        return person.getId() != null && person.getId() < 0;
    }

    /**
     * Switches to the "invite a new user" sub-step, prefilling the e-mail/username or last name
     * from whatever was typed in the members search field.
     */
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

    /** Discards the invite sub-form and returns to the member/profile selection step. */
    public void back() {
        clearInviteFields();
        step = WizardStep.MAIN;
    }

    /**
     * Runs whatever the current step's primary button means: validates and stages the invite draft
     * on the invite step, or creates/submits every selected member on the main step.
     */
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
        draftPasswords.put(draft.getId(), StringUtils.isBlank(invitePassword) ? null : invitePassword);
        selectedMembers.add(draft);

        searchQuery = null;
        clearInviteFields();
        step = WizardStep.MAIN;
    }

    private boolean inviteFieldsAreValid() {
        if (StringUtils.isBlank(inviteEmail) || StringUtils.isBlank(inviteFirstName)
                || StringUtils.isBlank(inviteLastName) || StringUtils.isBlank(inviteUsername)) {
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
        // The password is optional: when left empty, the member is created disabled and receives
        // an invitation e-mail to choose their own password.
        if (StringUtils.isNotBlank(invitePassword) || StringUtils.isNotBlank(invitePasswordConfirm)) {
            if (StringUtils.isBlank(invitePassword) || invitePassword.length() < 8) {
                displayErrorMessage(langBean, "userDialog.error.password");
                return false;
            }
            if (!invitePassword.equals(invitePasswordConfirm)) {
                displayErrorMessage(langBean, "userDialog.error.password.match");
                return false;
            }
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
     * existed and the ones just created — via the {@link ProcessPerson} callback.
     * Members that fail (creation error, or rejected by the callback) are kept so the user can retry.
     */
    private void confirmWizard() {
        List<PersonDTO> remaining = new ArrayList<>();
        int affected = 0;
        Set<ProfileDTO> profiles = new HashSet<>(selectedProfiles);

        for (PersonDTO candidate : selectedMembers) {
            boolean invitedWithoutPassword = isInvitedWithoutPassword(candidate);
            PersonDTO person = isDraft(candidate) ? createInvitedPerson(candidate) : candidate;
            if (person == null) {
                remaining.add(candidate);
                continue;
            }
            affected = processCreatedPerson(candidate, invitedWithoutPassword, person, profiles, affected, remaining);
        }

        selectedMembers = remaining;
        if (affected > 0 && selectedMembers.isEmpty()) {
            exit();
        }
    }

    private int processCreatedPerson(PersonDTO candidate, boolean invitedWithoutPassword, PersonDTO person, Set<ProfileDTO> profiles, int affected, List<PersonDTO> remaining) {
        if (invitedWithoutPassword) {
            // Created before the callback so the caller sees the pending invitation when it
            // refreshes its member list; the e-mail itself is only sent after the profiles are set.
            pendingPersonService.createOrGetInvitation(person);
        }
        Boolean added = processPerson.process(new PersonRole(person, null, profiles));
        if (Boolean.TRUE.equals(added)) {
            affected++;
            if (invitedWithoutPassword) {
                sendInvitation(person, profiles);
            }
        } else {
            remaining.add(candidate);
        }
        return affected;
    }

    private boolean isInvitedWithoutPassword(PersonDTO candidate) {
        return isDraft(candidate) && draftPasswords.get(candidate.getId()) == null;
    }

    private PersonDTO createInvitedPerson(PersonDTO draft) {
        String password = draftPasswords.get(draft.getId());

        PersonDTO person = new PersonDTO();
        person.setName(draft.getName());
        person.setLastname(draft.getLastname());
        person.setEmail(draft.getEmail());
        person.setUsername(draft.getUsername());
        person.setPassToModify(true);

        try {
            PersonDTO created = password == null
                    ? personService.createDisabledPersonWithRandomPassword(person)
                    : personService.createPerson(person, password);
            draftPasswords.remove(draft.getId());
            return created;
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

    /**
     * Sends the invitation e-mail to a member created without password, once their profiles have been
     * attributed by the {@link ProcessPerson} callback. The e-mail states who invited them, on which
     * scope (application / institution / project) and with which profiles; the registration link lets
     * them set their own password and enable their account.
     */
    private void sendInvitation(PersonDTO person, Set<ProfileDTO> profiles) {
        PendingPerson pendingPerson = pendingPersonService.createOrGetInvitation(person);
        String invitationLink = pendingPersonService.invitationLink(pendingPerson);
        String expirationDate = DateUtils.formatOffsetDateTime(pendingPerson.getPendingInvitationExpirationDate());
        String body = invitationEmailRenderer.render(inviterName(), invitationScopeName(),
                profilesLabel(profiles), invitationLink, expirationDate);
        try {
            emailManager.sendEmail(person.getEmail(),
                    invitationMailSubject(),
                    body,
                    EmailManager.TEXT_HTML);
            displayInfoMessage(langBean, "newMember.invitation.sent", person.getEmail());
        } catch (RuntimeException e) {
            // The member and their profiles are already persisted; only the e-mail delivery failed,
            // so keep going and let the manager know they may need to resend the invitation.
            log.error("Failed to send invitation e-mail to {}", person.getEmail(), e);
            displayErrorMessage(langBean, "newMember.invitation.failed", person.getEmail());
        }
    }

    /** @return the display name of the currently authenticated user who is sending the invitation. */
    private String inviterName() {
        PersonDTO inviter = sessionSettingsBean.getAuthenticatedUser();
        if (inviter == null) {
            return langBean.msg("mail.invitation.inviter.unknown");
        }
        String displayName = inviter.displayName();
        return StringUtils.isBlank(displayName) ? inviter.getUsername() : displayName.trim();
    }

    /**
     * @return a human-readable, comma-separated list of the roles granted to the invitee. Mirrors what
     * the member services actually persist: the implicit base "member" role of the scope is prepended
     * whenever the inviter did not already pick it (application-wide invitations have no such role).
     */
    private String profilesLabel(Set<ProfileDTO> profiles) {
        Set<ProfileDTO> effective = new LinkedHashSet<>();
        String memberCode = baseMemberProfileCode();
        if (memberCode != null && profiles.stream().noneMatch(p -> memberCode.equals(p.getCode()))) {
            availableProfiles.stream()
                    .filter(p -> memberCode.equals(p.getCode()))
                    .findFirst()
                    .ifPresent(effective::add);
        }
        effective.addAll(profiles);
        String label = effective.stream()
                .map(ProfileDTO::getName)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
        return StringUtils.isBlank(label) ? langBean.msg("mail.invitation.profiles.none") : label;
    }

    private static String usernameFromEmail(String email) {
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String sanitized = local.replaceAll("[^a-zA-Z0-9.]", ".");
        return sanitized.isBlank() ? "user" : sanitized;
    }

    /**
     * Builds the label shown on the wizard's primary action button, which depends on the current step
     * and, on the main step, on how many members are currently staged for submission.
     *
     * @return the localised label of the primary action button for the current step
     */
    public String getPrimaryActionLabel() {
        if (step == WizardStep.INVITE) {
            return langBean.msg("newOrganizationMember.action.invite");
        }
        return selectedMembers.isEmpty()
                ? langBean.msg("newOrganizationMember.action.add")
                : langBean.msg("newOrganizationMember.action.addCount", selectedMembers.size());
    }

    /**
     * Tells whether the primary action button should be enabled, i.e. whether it currently has work to do.
     *
     * @return {@code false} when the primary action would do nothing (no member selected on the main step),
     * {@code true} otherwise
     */
    public boolean isPrimaryActionEnabled() {
        return step == WizardStep.INVITE || !selectedMembers.isEmpty();
    }

    public enum WizardStep {
        MAIN,
        INVITE
    }

}
