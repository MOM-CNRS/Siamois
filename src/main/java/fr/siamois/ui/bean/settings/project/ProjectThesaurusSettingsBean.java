package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.domain.services.vocabulary.ThesaurusUsageService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.settings.AbstractThesaurusSettingsBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Optional;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;

@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ProjectThesaurusSettingsBean extends AbstractThesaurusSettingsBean implements Serializable {

    private final transient FieldConfigurationService fieldConfigurationService;
    private final transient ThesaurusUsageService thesaurusUsageService;
    private final ProjectTableFieldSettingsBean projectTableFieldSettingsBean;
    private ActionUnitDTO project;

    public ProjectThesaurusSettingsBean(FieldConfigurationService fieldConfigurationService, VocabularyService vocabularyService,
                                        LabelService labelService, ThesaurusUsageService thesaurusUsageService, LangBean langBean,
                                        ProjectTableFieldSettingsBean projectTableFieldSettingsBean) {
        super(vocabularyService, labelService, langBean);
        this.projectTableFieldSettingsBean = projectTableFieldSettingsBean;
        this.fieldConfigurationService = fieldConfigurationService;
        this.thesaurusUsageService = thesaurusUsageService;
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        resetState();
        project = null;
    }

    public void init(ActionUnitDTO project) {
        reset();
        this.project = project;
        Optional<String> optVocab = fieldConfigurationService.findVocabularyUrlOfActionUnitId(project.getId());
        optVocab.ifPresent(s -> thesaurusUrl = s);
        loadOptions();
        usages = new ArrayList<>(thesaurusUsageService.findUsagesOfActionUnit(project.getId(), langBean.getLanguageCode()));
    }

    /** Opens the field configuration of the project, where other thesauri (PACTOLS...) can be used. */
    public String goToFieldsConfig() {
        projectTableFieldSettingsBean.init(project);
        return "/pages/settings/project/projectTablesSettings.xhtml?faces-redirect=true";
    }

    public void saveConfig() {
        if (StringUtils.isEmpty(thesaurusUrl)) return;

        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(thesaurusUrl);
            fieldConfigurationService.setupFieldConfigurationForActionUnit(project, vocabulary, progressWrapper);
            notifySaved();
            usages = new ArrayList<>(thesaurusUsageService.findUsagesOfActionUnit(project.getId(), langBean.getLanguageCode()));
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
