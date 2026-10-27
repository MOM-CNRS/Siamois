package fr.siamois.ui.bean.settings;

import fr.siamois.domain.events.publisher.LangageChangeEventPublisher;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.token.PersonActionToken;
import fr.siamois.domain.models.auth.token.PersonActionType;
import fr.siamois.domain.models.events.InstitutionChangeEvent;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.auth.EmailAlreadyExistException;
import fr.siamois.domain.models.exceptions.auth.InvalidEmailException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.settings.PersonSettings;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.auth.PersonActionTokenService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.email.AccountActionMailer;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;

@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ProfileSettingsBean implements Serializable {

    public static final String DEFAULT_THEME = "siamois-day";

    private final SessionSettingsBean sessionSettingsBean;
    private final transient PersonService personService;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final transient VocabularyService vocabularyService;
    private final transient InstitutionService institutionService;
    private final transient LangService langService;
    private final LangBean langBean;
    private final transient LangageChangeEventPublisher langageChangeEventPublisher;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient ActionUnitService actionUnitService;
    private final transient NavBean navBean;
    private final InstitutionListSettingsBean institutionListSettingsBean;
    private final transient PersonActionTokenService personActionTokenService;
    private final transient AccountActionMailer accountActionMailer;

    private PersonSettings personSettings;
    private Concept refConfigConcept;

    /** Identity shown read-only in the profile section. */
    private String fLastname;
    private String fFirstname;
    private String fEmail;

    private String fSelectedLang;
    /** The only theme for now; not persisted yet. */
    private String fSelectedTheme = DEFAULT_THEME;

    private String fThesaurusUrl;

    private ProgressWrapper progressWrapper = new ProgressWrapper();

    /** The profiles the user holds, where each applies and what it grants — the "my rights" dashboard. */
    private transient List<ProfilePermissionService.ProfileGrant> rights = List.of();

    @EventListener(InstitutionChangeEvent.class)
    public void init() {
        UserInfo info = sessionSettingsBean.getUserInfo();
        PersonDTO user = info.getUser();
        initPersonSection(info);
        initThesaurusSection(info);
        rights = profilePermissionService.grantsOf(user);

        fSelectedLang = langBean.getLanguageCode();
    }

    private void initThesaurusSection(UserInfo info) {
        try {

            // TODO : store the user thesaurus URL in its profil? to be discussed with Julien. We don't get consistent thesaurus URL for user;
            //  It's changing when we are in different institutions
            refConfigConcept = fieldConfigurationService.findParentConceptForFieldcode(info, SpatialUnit.TYPE_FIELD_CODE);
            fThesaurusUrl = refConfigConcept.getVocabulary().getUri();
        } catch (NoConfigForFieldException e) {
            log.warn("User has no thesaurus configuration for fieldCode {}", SpatialUnit.TYPE_FIELD_CODE);
        }
    }

    private void initPersonSection(UserInfo info) {
        PersonDTO user = info.getUser();
        fEmail = user.getEmail();
        fLastname = user.getLastname();
        fFirstname = user.getName();
        personSettings = personService.createOrGetSettingsOf(user);
    }

    /** The label of a permission code, falling back to the code itself when it has no translation. */
    public String permissionLabel(String code) {
        String key = "permission." + code;
        String label = langBean.msg(key);
        return label == null || label.equals(key) ? code : label;
    }

    /** Whether the grant points at a project or an organisation the user can jump to. */
    public boolean canOpen(ProfilePermissionService.ProfileGrant grant) {
        return grant.getActionUnitId() != null || grant.getInstitutionId() != null;
    }

    /** Quick access from the dashboard: opens the settings of the project or organisation of a grant. */
    public void open(ProfilePermissionService.ProfileGrant grant) throws IOException {
        if (grant.getActionUnitId() != null) {
            navBean.redirectToActionUnitSettings(actionUnitService.findById(grant.getActionUnitId()));
            return;
        }
        if (grant.getInstitutionId() == null) {
            return;
        }
        institutionListSettingsBean.init();
        String path = institutionListSettingsBean.redirectToInstitution(institutionService.findById(grant.getInstitutionId()));
        if (path == null) {
            return;
        }
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        externalContext.redirect(externalContext.getRequestContextPath() + path);
    }

    /**
     * Asks to change the e-mail address: nothing is modified now, a confirmation link is sent to the
     * new address and the change is applied when it is followed.
     */
    public void requestEmailChange() {
        PersonDTO user = sessionSettingsBean.getAuthenticatedUser();
        String newEmail = StringUtils.trimToEmpty(fEmail);
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_WARN, "myProfile.message.unchanged");
            return;
        }
        try {
            personService.checkNewEmail(newEmail);
        } catch (EmailAlreadyExistException e) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_ERROR, "register.error.email.alreadyexists", newEmail);
            return;
        } catch (InvalidEmailException e) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_ERROR, "register.error.email");
            return;
        }
        if (!personActionTokenService.canIssue(user.getId(), PersonActionType.EMAIL_CHANGE)) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_WARN, "myProfile.mail.tooSoon");
            return;
        }
        PersonActionToken token = personActionTokenService.issue(user.getId(), PersonActionType.EMAIL_CHANGE, newEmail);
        if (accountActionMailer.send(token, newEmail)) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "myProfile.email.message.sent", newEmail);
        } else {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_ERROR, "myProfile.mail.error");
        }
        fEmail = user.getEmail();
    }

    /** Sends a link to the current address, letting the user choose a new password. */
    public void requestPasswordReset() {
        PersonDTO user = sessionSettingsBean.getAuthenticatedUser();
        if (!personActionTokenService.canIssue(user.getId(), PersonActionType.PASSWORD_RESET)) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_WARN, "myProfile.mail.tooSoon");
            return;
        }
        PersonActionToken token = personActionTokenService.issue(user.getId(), PersonActionType.PASSWORD_RESET, null);
        if (accountActionMailer.send(token, user.getEmail())) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "myProfile.password.message.sent", user.getEmail());
        } else {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_ERROR, "myProfile.mail.error");
        }
    }

    public void saveThesaurusUserConfig() {
        MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_WARN, "common.warn.notImplemented");
    }

    public List<Locale> getRefLangs() {
        List<String> langCodes = List.of(langService.getAvailableLanguages());
        return langCodes.stream()
                .map(Locale::new)
                .toList();
    }

    public String localeToLangName(Locale locale) {
        if (locale == null) {
            return "NULL";
        }
        return StringUtils.capitalize(locale.getDisplayName(locale));
    }

    public String localeToLangCode(Locale locale) {
        if (locale == null) {
            return "NULL";
        }
        return locale.getLanguage();
    }

    public String codeToLangName(String code) {
        return localeToLangName(new Locale(code));
    }

    public void savePreferences() {
        if (!fSelectedLang.equalsIgnoreCase(personSettings.getLangCode())) {
            personSettings.setLangCode(fSelectedLang);
        }

        personSettings = personService.updatePersonSettings(personSettings);
        langageChangeEventPublisher.publishInstitutionChangeEvent();

        MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "myProfile.preferences.message.success");
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        fEmail = null;
        fLastname = null;
        fFirstname = null;
        fThesaurusUrl = null;
        fSelectedLang = null;
        personSettings = null;
        refConfigConcept = null;
        progressWrapper = new ProgressWrapper();
    }
}
