package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.vocabulary.ThesaurusUsage;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.domain.services.vocabulary.ThesaurusUsageService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.PrimeFaces;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;

/**
 * State shared by the project and organisation thesaurus pages: the thesaurus picked in the list of
 * those known to the database, the small "add a thesaurus" form, and the usages overview.
 */
@Getter
@Setter
@Slf4j
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
    /** The thesaurus last applied, to tell whether the selection has changed since. */
    protected String savedThesaurusUrl;
    /** The URL typed in the "add a thesaurus" dialog. */
    protected String newThesaurusUrl;
    protected ArrayList<ThesaurusOption> thesaurusOptions = new ArrayList<>();
    protected ArrayList<ThesaurusUsage> usages = new ArrayList<>();
    /** The rows ticked in the usages table. */
    private List<ThesaurusUsage> selectedUsages = new ArrayList<>();
    /** The rows left by the filters of the usages table, null while none is applied. */
    private List<ThesaurusUsage> filteredUsages;
    protected ProgressWrapper progressWrapper = new ProgressWrapper();
    /** The stored sources that read the thesaurus just left, offered to move to the one just applied. */
    private List<ThesaurusUsage> rewireRows = new ArrayList<>();
    private String rewireOldLabel;
    private String rewireNewLabel;
    private String rewireNewUrl;

    protected AbstractThesaurusSettingsBean(VocabularyService vocabularyService, LabelService labelService, LangBean langBean) {
        this.vocabularyService = vocabularyService;
        this.labelService = labelService;
        this.langBean = langBean;
    }

    protected void resetState() {
        thesaurusUrl = null;
        savedThesaurusUrl = null;
        newThesaurusUrl = null;
        thesaurusOptions = new ArrayList<>();
        usages = new ArrayList<>();
        selectedUsages = new ArrayList<>();
        filteredUsages = null;
        rewireRows = new ArrayList<>();
        progressWrapper.reset();
    }

    /** Moves the given fields to the target thesaurus, returning what was done. */
    protected abstract ThesaurusUsageService.RewireResult rewire(List<ThesaurusUsage> rows, Vocabulary target);

    /** Reads the usages again, once fields were moved. */
    protected abstract void reloadUsages();

    /**
     * Once a new thesaurus is applied, offers to move the fields that still read the old one. Does nothing when no field does.
     *
     * @param oldUrl the thesaurus applied before
     * @param newUrl the thesaurus just applied
     */
    protected void askToRewire(String oldUrl, String newUrl) {
        rewireRows = new ArrayList<>();
        if (StringUtils.isBlank(oldUrl) || oldUrl.equals(newUrl)) return;
        rewireRows = usages.stream()
                .filter(usage -> !usage.isInherited() && oldUrl.equals(usage.getThesaurusUrl()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (rewireRows.isEmpty()) return;
        rewireOldLabel = labelOf(oldUrl);
        rewireNewLabel = labelOf(newUrl);
        rewireNewUrl = newUrl;
        PrimeFaces.current().executeScript("PF('rewireAskDialog').show();");
    }

    private String labelOf(String url) {
        return thesaurusOptions.stream().filter(option -> option.getUrl().equals(url)).map(ThesaurusOption::getLabel).findFirst().orElse(url);
    }

    /** The user accepted: the offered fields read the new thesaurus. */
    public void confirmRewire() {
        List<ThesaurusUsage> rows = rewireRows;
        rewireRows = new ArrayList<>();
        Optional<Vocabulary> target = vocabularyService.findAll().stream().filter(v -> urlOf(v).equals(rewireNewUrl)).findFirst();
        if (rows.isEmpty() || target.isEmpty()) return;
        try {
            ThesaurusUsageService.RewireResult result = rewire(rows, target.get());
            MessageUtils.displayInfoMessage(langBean, "thesaurusUsages.bulk.done", result.rewired(), result.codeMissing(), result.ignored());
        } catch (RuntimeException e) {
            log.error("Could not connect the fields to {}", rewireNewUrl, e);
            displayErrorMessage(langBean, "thesaurusUsages.bulk.error");
        }
        reloadUsages();
    }

    /** The user declined: the fields keep reading the old thesaurus. */
    public void declineRewire() {
        rewireRows = new ArrayList<>();
    }

    protected void loadOptions() {
        thesaurusOptions = vocabularyService.findAll().stream()
                .map(v -> new ThesaurusOption(urlOf(v), labelService.findLabelOf(v, langBean.getLanguageCode()).getValue()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
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

    /** Whether a thesaurus other than the applied one is selected: the changes then wait to be applied. */
    public boolean isThesaurusChanged() {
        return StringUtils.isNotBlank(thesaurusUrl) && !thesaurusUrl.equals(savedThesaurusUrl);
    }

    protected void notifySaved() {
        savedThesaurusUrl = thesaurusUrl;
        MessageUtils.displayInfoMessage(langBean, "myProfile.thesaurus.message.success");
    }
}
