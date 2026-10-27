package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.ThesaurusConfigService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The "OpenTheso API key" section of the profile page: one key per thesaurus attached to an organisation
 * or a project whose settings the user manages.
 */
@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class OpenthesoApiKeySettingsBean implements Serializable {

    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient VocabularyService vocabularyService;
    private final transient ThesaurusConfigService thesaurusConfigService;

    private List<ThesaurusApiKey> thesauri = List.of();

    public void init() {
        // a postback must not rebuild the rows: it would drop the keys just typed
        if (FacesContext.getCurrentInstance().isPostback()) {
            return;
        }
        PersonDTO user = sessionSettingsBean.getAuthenticatedUser();
        ProfilePermissionService.SettingsScope scope = profilePermissionService.settingsScopeOf(user);
        // a key already saved is shown, masked, in its field
        Map<Long, String> savedKeys = thesaurusConfigService.findApiKeysOf(user.getId());
        thesauri = vocabularyService.findDocumentationThesauriIn(scope, langBean.getLanguageCode()).stream()
                .map(info -> new ThesaurusApiKey(info.vocabularyId(), info.thesaurus().label(),
                        info.thesaurus().completeThesaurusUrl(), savedKeys.get(info.vocabularyId())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public void saveApiKeys() {
        try {
            thesaurusConfigService.updateThesaurusApiKeyConfig(thesauri);
            MessageUtils.displayInfoMessage(langBean, "myProfile.openthesoApiKey.saved");
        } catch (RuntimeException e) {
            log.error("Could not save the OpenTheso API keys", e);
            MessageUtils.displayErrorMessage(langBean, "common.error.internal");
        }
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        thesauri = List.of();
    }
}
