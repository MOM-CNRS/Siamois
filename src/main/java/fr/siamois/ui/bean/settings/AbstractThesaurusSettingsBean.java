package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.vocabulary.ThesaurusUsage;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;

/**
 * State shared by the project and organisation thesaurus pages: the thesaurus picked in the list of
 * those known to the database, the small "add a thesaurus" form, and the usages overview.
 */
@Getter
@Setter
public abstract class AbstractThesaurusSettingsBean implements Serializable {

    /** A thesaurus of the database, as offered by the drop-down; {@code url} is the value stored in {@code thesaurusUrl}. */
    @Value
    public static class ThesaurusOption implements Serializable {
        String url;
        String label;
    }

    protected final transient VocabularyService vocabularyService;
    protected final transient LabelService labelService;
    protected final LangBean langBean;

    /** The selected thesaurus, as an OpenTheso URL. */
    protected String thesaurusUrl;
    /** The URL typed in the "add a thesaurus" dialog. */
    protected String newThesaurusUrl;
    protected List<ThesaurusOption> thesaurusOptions = new ArrayList<>();
    protected List<ThesaurusUsage> usages = new ArrayList<>();
    protected ProgressWrapper progressWrapper = new ProgressWrapper();

    protected AbstractThesaurusSettingsBean(VocabularyService vocabularyService, LabelService labelService, LangBean langBean) {
        this.vocabularyService = vocabularyService;
        this.labelService = labelService;
        this.langBean = langBean;
    }

    protected void resetState() {
        thesaurusUrl = null;
        newThesaurusUrl = null;
        thesaurusOptions = new ArrayList<>();
        usages = new ArrayList<>();
        progressWrapper.reset();
    }

    protected void loadOptions() {
        thesaurusOptions = vocabularyService.findAll().stream()
                .map(v -> new ThesaurusOption(urlOf(v), labelService.findLabelOf(v, langBean.getLanguageCode()).getValue()))
                .toList();
    }

    protected static String urlOf(Vocabulary v) {
        return v.getBaseUri() + "/?idt=" + v.getExternalVocabularyId();
    }

    /** Registers the thesaurus typed in the dialog, then selects it. */
    public void addThesaurus() {
        if (StringUtils.isBlank(newThesaurusUrl)) return;
        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(newThesaurusUrl.trim());
            loadOptions();
            thesaurusUrl = urlOf(vocabulary);
            newThesaurusUrl = null;
        } catch (InvalidEndpointException e) {
            displayErrorMessage(langBean, "myProfile.thesaurus.uri.invalid");
        }
    }

    protected void notifySaved() {
        MessageUtils.displayInfoMessage(langBean, "myProfile.thesaurus.message.success");
    }
}
