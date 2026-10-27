package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.vocabulary.ThesaurusUsage;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.domain.services.vocabulary.ThesaurusUsageService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.settings.AbstractThesaurusSettingsBean;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.model.SelectItem;
import jakarta.faces.model.SelectItemGroup;
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
import java.util.List;
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
    /** The thesaurus, as an OpenTheso URL, the ticked fields are connected to. */
    private String bulkTargetUrl;

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
        bulkTargetUrl = null;
    }

    public void init(ActionUnitDTO project) {
        reset();
        this.project = project;
        Optional<String> optVocab = fieldConfigurationService.findVocabularyUrlOfActionUnitId(project.getId());
        optVocab.ifPresent(s -> thesaurusUrl = s);
        savedThesaurusUrl = thesaurusUrl;
        loadOptions();
        reloadUsages();
    }

    @Override
    protected void reloadUsages() {
        usages = new ArrayList<>(thesaurusUsageService.findAllUsagesOfProject(project.getId(), institutionId(), langBean.getLanguageCode()));
        setSelectedUsages(new ArrayList<>());
        setFilteredUsages(null);
    }

    private Long institutionId() {
        return project.getCreatedByInstitution() == null ? null : project.getCreatedByInstitution().getId();
    }

    /**
     * The thesauri a field code can be moved to: the one the project (or its institution) documents its own fields and
     * types in first, as it is where a team that builds its vocabulary wants its fields to point, then the others known here.
     */
    public List<SelectItem> getBulkTargetItems() {
        String documentation = fieldConfigurationService.findVocabularyUrlOfActionUnitId(project.getId())
                .or(() -> institutionId() == null ? Optional.empty() : fieldConfigurationService.findVocabularyUrlOfInstitutionId(institutionId()))
                .orElse(null);
        List<SelectItem> mine = new ArrayList<>();
        List<SelectItem> others = new ArrayList<>();
        for (ThesaurusOption option : thesaurusOptions) {
            SelectItem item = new SelectItem(option.getUrl(), option.getLabel());
            (option.getUrl().equals(documentation) ? mine : others).add(item);
        }
        List<SelectItem> items = new ArrayList<>();
        if (!mine.isEmpty()) {
            SelectItemGroup group = new SelectItemGroup(langBean.msg("thesaurusUsages.bulk.group.documentation"));
            group.setSelectItems(mine.toArray(new SelectItem[0]));
            items.add(group);
        }
        if (!others.isEmpty()) {
            SelectItemGroup group = new SelectItemGroup(langBean.msg("thesaurusUsages.bulk.group.others"));
            group.setSelectItems(others.toArray(new SelectItem[0]));
            items.add(group);
        }
        return items;
    }

    /** Connects the ticked fields that use a field code to the same codes in the picked thesaurus. */
    public void rewireSelected() {
        if (StringUtils.isBlank(bulkTargetUrl) || getSelectedUsages() == null || getSelectedUsages().isEmpty()) {
            displayErrorMessage(langBean, "thesaurusUsages.bulk.nothing");
            return;
        }
        Optional<Vocabulary> target = vocabularyService.findAll().stream().filter(v -> urlOf(v).equals(bulkTargetUrl)).findFirst();
        if (target.isEmpty() || institutionId() == null) {
            displayErrorMessage(langBean, "thesaurusUsages.bulk.nothing");
            return;
        }
        try {
            ThesaurusUsageService.RewireResult result = thesaurusUsageService.rewireFieldCodes(project.getId(), institutionId(), getSelectedUsages(), target.get());
            MessageUtils.displayInfoMessage(langBean, "thesaurusUsages.bulk.done", result.rewired(), result.codeMissing(), result.ignored());
        } catch (RuntimeException e) {
            log.error("Could not connect the fields to {}", bulkTargetUrl, e);
            displayErrorMessage(langBean, "thesaurusUsages.bulk.error");
        }
        reloadUsages();
    }

    /** Opens the field configuration of the project, where other thesauri (PACTOLS...) can be used. */
    public String goToFieldsConfig() {
        projectTableFieldSettingsBean.init(project);
        return "/pages/settings/project/projectTablesSettings.xhtml?faces-redirect=true";
    }

    @Override
    protected ThesaurusUsageService.RewireResult rewire(List<ThesaurusUsage> rows, Vocabulary target) {
        return thesaurusUsageService.rewireFieldCodes(project.getId(), institutionId(), rows, target);
    }

    public void saveConfig() {
        applyThesaurus(vocabulary -> fieldConfigurationService.setupFieldConfigurationForActionUnit(project, vocabulary, progressWrapper));
    }

}
