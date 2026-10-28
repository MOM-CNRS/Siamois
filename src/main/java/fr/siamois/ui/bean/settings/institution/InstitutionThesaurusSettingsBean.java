package fr.siamois.ui.bean.settings.institution;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.vocabulary.ThesaurusUsage;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.domain.services.vocabulary.ThesaurusUsageService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.AbstractThesaurusSettingsBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class InstitutionThesaurusSettingsBean extends AbstractThesaurusSettingsBean implements Serializable {

    private final transient FieldConfigurationService fieldConfigurationService;
    private final SessionSettingsBean sessionSettingsBean;
    private final transient ThesaurusUsageService thesaurusUsageService;
    private InstitutionDTO institution;

    public InstitutionThesaurusSettingsBean(FieldConfigurationService fieldConfigurationService, SessionSettingsBean sessionSettingsBean,
                                            VocabularyService vocabularyService, LabelService labelService,
                                            ThesaurusUsageService thesaurusUsageService, LangBean langBean) {
        super(vocabularyService, labelService, langBean);
        this.fieldConfigurationService = fieldConfigurationService;
        this.sessionSettingsBean = sessionSettingsBean;
        this.thesaurusUsageService = thesaurusUsageService;
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        resetState();
        institution = null;
    }

    public void init(InstitutionDTO institution) {
        reset();
        this.institution = institution;
        Optional<String> optVocab = fieldConfigurationService.findVocabularyUrlOfInstitutionId(institution.getId());
        optVocab.ifPresent(s -> thesaurusUrl = s);
        savedThesaurusUrl = thesaurusUrl;
        loadOptions();
        reloadUsages();
    }

    @Override
    protected void reloadUsages() {
        usages = new ArrayList<>(thesaurusUsageService.findUsagesOfInstitution(institution.getId(), langBean.getLanguageCode()));
        setSelectedUsages(new ArrayList<>());
        setFilteredUsages(null);
    }

    @Override
    protected ThesaurusUsageService.RewireResult rewire(List<ThesaurusUsage> rows, Vocabulary target) {
        return thesaurusUsageService.rewireFieldCodes(null, institution.getId(), rows, target);
    }

    public void saveConfig() {
        applyThesaurus(vocabulary -> fieldConfigurationService.setupFieldConfigurationForInstitution(
                sessionSettingsBean.getUserInfo(), vocabulary, progressWrapper));
    }

    public void synchronizeConfig() {
        synchronizeThesaurus(vocabulary -> fieldConfigurationService.loadFieldCodes(vocabulary, progressWrapper));
    }

}
