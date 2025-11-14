package fr.siamois.ui.bean.settings;

import fr.siamois.domain.events.publisher.LangageChangeEventPublisher;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.events.InstitutionChangeEvent;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.auth.InvalidNameException;
import fr.siamois.domain.models.exceptions.auth.InvalidUserInformationException;
import fr.siamois.domain.models.exceptions.auth.UserAlreadyExistException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.settings.PersonSettings;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.application.FacesMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;

@Slf4j
@Getter
@Setter
@Component
@SessionScoped
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

    private Set<Institution> refInstitutions;
    private PersonSettings personSettings;
    private Concept refConfigConcept;

    private String fEmail;
    private String fLastname;
    private String fFirstname;

    private String fSelectedLang;

    private String fThesaurusUrl;
    private Long fDefaultInstitutionId;

    private ProgressWrapper progressWrapper = new ProgressWrapper();

    @EventListener(InstitutionChangeEvent.class)
    public void init() {
        UserInfo info = sessionSettingsBean.getUserInfo();
        Person user = info.getUser();
        initPersonSection(info);
        initThesaurusSection(info);
        initInstitutions(user, info);

        fSelectedLang = langBean.getLanguageCode();
    }

    private void initInstitutions(Person user, UserInfo info) {
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
            refConfigConcept = fieldConfigurationService.findParentConceptForFieldcode(info, SpatialUnit.CATEGORY_FIELD_CODE);
            fThesaurusUrl = refConfigConcept.getVocabulary().getUri();
        } catch (NoConfigForFieldException e) {
            log.warn("User has no thesaurus configuration for fieldCode {}", SpatialUnit.CATEGORY_FIELD_CODE);
        }
    }

    private void initPersonSection(UserInfo info) {
        Person user = info.getUser();
        fEmail = user.getEmail();
        fLastname = user.getLastname();
        fFirstname = user.getName();
        personSettings = personService.createOrGetSettingsOf(user);
    }

    public void saveProfile() {
        boolean updated = false;
        Person user = sessionSettingsBean.getAuthenticatedUser();
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
        if (fThesaurusUrl == null || fThesaurusUrl.isEmpty()) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_WARN, "myProfile.thesaurus.message.missing");
            return;
        }

        if (refConfigConcept != null && refConfigConcept.getVocabulary().getUri().equals(fThesaurusUrl)) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_WARN, "myProfile.thesaurus.message.unchanged");
            return;
        }

        UserInfo info = sessionSettingsBean.getUserInfo();
        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(fThesaurusUrl);
            fieldConfigurationService.setupFieldConfigurationForUser(info, vocabulary, progressWrapper);
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "myProfile.thesaurus.message.success");
            refConfigConcept = fieldConfigurationService.findParentConceptForFieldcode(info, SpatialUnit.CATEGORY_FIELD_CODE);
        } catch (InvalidEndpointException e) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_ERROR, "myProfile.thesaurus.uri.invalid");
        } catch (NotSiamoisThesaurusException e) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_ERROR, "myProfile.thesaurus.siamois.invalid");
        } catch (ErrorProcessingExpansionException e) {
            displayErrorMessage(langBean, "thesaurus.error.processingExpansion");
        } catch (NoConfigForFieldException e) {
            log.error("Unexpected error after setting up thesaurus for user", e);
            displayErrorMessage(langBean, "common.error.internal");
        } finally {
            progressWrapper.reset();
        }
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

    private Institution findInstitutionById(Long id) {
        return refInstitutions.stream()
                .filter(institution -> institution.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Institution not found"));
    }

    public void savePreferences() {
        personSettings.setDefaultInstitution(findInstitutionById(fDefaultInstitutionId));
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
