package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Optional;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;

@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ProjectThesaurusSettingsBean implements Serializable {

    private final transient FieldConfigurationService fieldConfigurationService;
    private final transient VocabularyService vocabularyService;
    private final LangBean langBean;
    private String thesaurusUrl;
    private ActionUnitDTO project;

    private ProgressWrapper progressWrapper = new ProgressWrapper();

    public ProjectThesaurusSettingsBean(FieldConfigurationService fieldConfigurationService, VocabularyService vocabularyService, LangBean langBean) {
        this.fieldConfigurationService = fieldConfigurationService;
        this.vocabularyService = vocabularyService;
        this.langBean = langBean;
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        thesaurusUrl = null;
        project = null;
        progressWrapper.reset();
    }

    public void init(ActionUnitDTO project) {
        reset();
        this.project = project;
        Optional<String> optVocab = fieldConfigurationService.findVocabularyUrlOfActionUnitId(project.getId());
        optVocab.ifPresent(s -> thesaurusUrl = s);
        progressWrapper.reset();
    }

    public void saveConfig() {
        if (StringUtils.isEmpty(thesaurusUrl)) return;

        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(thesaurusUrl);
            fieldConfigurationService.setupFieldConfigurationForActionUnit(project, vocabulary, progressWrapper);
            MessageUtils.displayInfoMessage(langBean, "myProfile.thesaurus.message.success");
        } catch (InvalidEndpointException e) {
            displayErrorMessage(langBean, "myProfile.thesaurus.uri.invalid");
        } catch (NotSiamoisThesaurusException e) {
            displayErrorMessage(langBean, "myProfile.thesaurus.siamois.invalid");
        } catch (ErrorProcessingExpansionException e) {
            displayErrorMessage(langBean, "thesaurus.error.processingExpansion");
        } finally {
            progressWrapper.reset();
        }


    }

}
