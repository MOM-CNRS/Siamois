package fr.siamois.ui.bean.settings;

import fr.siamois.domain.events.publisher.LangageChangeEventPublisher;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.events.InstitutionChangeEvent;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.auth.InvalidNameException;
import fr.siamois.domain.models.exceptions.auth.InvalidUserInformationException;
import fr.siamois.domain.models.exceptions.auth.UserAlreadyExistException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.settings.PersonSettings;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
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
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ProfileSettingsBean implements Serializable {

    private final SessionSettingsBean sessionSettingsBean;
    private final transient PersonService personService;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final transient VocabularyService vocabularyService;
    private final transient InstitutionService institutionService;
    private final transient LangService langService;
    private final LangBean langBean;
    private final transient LangageChangeEventPublisher langageChangeEventPublisher;
    private final transient ConversionService conversionService;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient ActionUnitService actionUnitService;
    private final transient NavBean navBean;
    private final InstitutionListSettingsBean institutionListSettingsBean;

    private Set<InstitutionDTO> refInstitutions;
    private PersonSettings personSettings;
    private Concept refConfigConcept;

    private String fEmail;
    private String fLastname;
    private String fFirstname;

    private String fSelectedLang;

    private String fThesaurusUrl;
    private Long fDefaultInstitutionId;

    private ProgressWrapper progressWrapper = new ProgressWrapper();

    /** The profiles the user holds, where each applies and what it grants — the "my rights" dashboard. */
    private transient List<ProfilePermissionService.ProfileGrant> rights = List.of();

    @EventListener(InstitutionChangeEvent.class)
    public void init() {
        UserInfo info = sessionSettingsBean.getUserInfo();
        PersonDTO user = info.getUser();
        initPersonSection(info);
        initThesaurusSection(info);
        initInstitutions(user, info);
        rights = profilePermissionService.grantsOf(user);

        fSelectedLang = langBean.getLanguageCode();
    }

    private void initInstitutions(PersonDTO user, UserInfo info) {
        refInstitutions = institutionService.findInstitutionsOfPerson(user);
        PersonSettings settings = personService.createOrGetSettingsOf(user);
        if (settings.getDefaultInstitution() != null) {
            fDefaultInstitutionId = settings.getDefaultInstitution().getId();
        } else {
            fDefaultInstitutionId = info.getInstitution().getId();
        }
        log.trace("Found {} institutions", refInstitutions.size());
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

    public void saveProfile() {
        boolean updated = false;
        PersonDTO user = sessionSettingsBean.getAuthenticatedUser();
        if (fEmail != null && !fEmail.isEmpty() && !fEmail.equals(user.getEmail())) {
            user.setEmail(fEmail);
            updated = true;
        }

        if (fLastname != null && !fLastname.isEmpty() && !fLastname.equals(user.getLastname())) {
            user.setLastname(fLastname);
            updated = true;
        }

        if (fFirstname != null && !fFirstname.isEmpty() && !fFirstname.equals(user.getName())) {
            user.setName(fFirstname);
            updated = true;
        }

        if (!updated) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_WARN, "myProfile.message.unchanged");
            return;
        }

        try {
            personService.updatePerson(user);
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "myProfile.message.success");
        } catch (InvalidNameException e) {
            log.error(e.getMessage());
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_ERROR, "common.entity.person.invalidname", Person.NAME_MAX_LENGTH);
        } catch (InvalidUserInformationException | UserAlreadyExistException e) {
            log.error("There was a problem while updating the person", e);
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_ERROR, "common.error.internal");
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

    private InstitutionDTO findInstitutionById(Long id) {
        return refInstitutions.stream()
                .filter(institution -> institution.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Institution not found"));
    }


    public void savePreferences() {
        personSettings.setDefaultInstitution(
                conversionService.convert(findInstitutionById(fDefaultInstitutionId),Institution.class)
                );
        if (!fSelectedLang.equalsIgnoreCase(personSettings.getLangCode())) {
            personSettings.setLangCode(fSelectedLang);
        }

        personSettings = personService.updatePersonSettings(personSettings);
        langageChangeEventPublisher.publishInstitutionChangeEvent();

        MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "myProfile.preferences.message.success");
    }

    public String labelOfInstitutionWithId(Long id) {
        return findInstitutionById(id).getName();
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        fEmail = null;
        fLastname = null;
        fFirstname = null;
        fThesaurusUrl = null;
        fSelectedLang = null;
        fDefaultInstitutionId = null;
        personSettings = null;
        refConfigConcept = null;
        refInstitutions = null;
        progressWrapper = new ProgressWrapper();
    }
}
