package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.vocabulary.ThesaurusInfo;
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
        /** The identifier of the thesaurus on its instance (th332). */
        String externalId;
        /** The address of the OpenTheso instance. */
        String server;
    }

    protected final transient VocabularyService vocabularyService;
    protected final transient LabelService labelService;
    protected final LangBean langBean;

    /** The selected thesaurus, as an OpenTheso URL. */
    protected String thesaurusUrl;
    /** The thesaurus last applied, to tell whether the selection has changed since. */
    protected String savedThesaurusUrl;
    private static final String DEFAULT_THESAURUS_SERVER = "https://thesaurus.mom.fr";
    /** The instance and the thesaurus picked in the "add a thesaurus" dialog, and the thesauri of that instance. */
    private String pickedServer;
    private transient ThesaurusInfo pickedThesaurus;
    private transient List<ThesaurusInfo> pickedServerThesauri = new ArrayList<>();
    private transient List<String> serverOptions;
    /** The address typed in the "add an instance" dialog. */
    private String newServerUrl;
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
        pickedServer = null;
        pickedThesaurus = null;
        pickedServerThesauri = new ArrayList<>();
        serverOptions = null;
        newServerUrl = null;
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
        return thesaurusOptions.stream()
                .filter(option -> option.getUrl().equals(url))
                .map(option -> option.getLabel() + " (" + option.getExternalId() + ")")
                .findFirst()
                .orElse(url);
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
                .map(v -> new ThesaurusOption(urlOf(v), labelService.findLabelOf(v, langBean.getLanguageCode()).getValue(),
                        v.getExternalVocabularyId(), ThesaurusServers.normalize(v.getBaseUri())))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    protected static String urlOf(Vocabulary v) {
        return v.getBaseUri() + "/?idt=" + v.getExternalVocabularyId();
    }

    /** The instances to pick from: the default one and those of the thesauri the database knows. */
    public List<String> getServerOptions() {
        if (serverOptions == null) {
            serverOptions = ThesaurusServers.known(vocabularyService, DEFAULT_THESAURUS_SERVER);
        }
        return serverOptions;
    }

    /** The instances matching what was typed. */
    public List<String> completeServers(String query) {
        return ThesaurusServers.matching(getServerOptions(), query);
    }

    /** The thesauri of the picked instance matching what was typed, by label or identifier. */
    public List<ThesaurusInfo> completeThesauri(String query) {
        return ThesaurusServers.matchingThesauri(pickedServerThesauri == null ? List.of() : pickedServerThesauri, query);
    }

    /** Another instance was picked: its thesauri are listed, and none is picked yet. */
    public void onServerPicked() {
        pickedThesaurus = null;
        pickedServerThesauri = new ArrayList<>();
        if (StringUtils.isBlank(pickedServer)) return;
        try {
            pickedServerThesauri = new ArrayList<>(vocabularyService.findAllThesaurusOf(ThesaurusServers.normalize(pickedServer), langBean.getLanguageCode()));
        } catch (InvalidEndpointException e) {
            log.error("Could not list the thesauri of {}", pickedServer, e);
            displayErrorMessage(langBean, "projectTables.drawer.params.serverUnreachable");
        }
    }

    /** Registers an instance typed by the user, once it answers, and picks it. */
    public void addServer() {
        if (StringUtils.isBlank(newServerUrl)) return;
        String server = ThesaurusServers.normalize(newServerUrl);
        try {
            vocabularyService.findAllThesaurusOf(server, langBean.getLanguageCode());
        } catch (InvalidEndpointException e) {
            log.error("Could not reach the OpenTheso instance {}", server, e);
            displayErrorMessage(langBean, "projectTables.drawer.params.serverUnreachable");
            return;
        }
        if (!getServerOptions().contains(server)) {
            serverOptions.add(server);
        }
        newServerUrl = null;
        pickedServer = server;
        onServerPicked();
    }

    /** What sets a scope (a project, an organisation) up on the thesaurus just chosen. */
    @FunctionalInterface
    protected interface ThesaurusSetup {
        void run(Vocabulary vocabulary) throws NotSiamoisThesaurusException, ErrorProcessingExpansionException;
    }

    /**
     * Applies the selected thesaurus: sets the scope up on it, refreshes the usages, then offers to move the fields that
     * still read the thesaurus left. A failure is reported to the user, and leaves the selection as it was.
     *
     * @param setup how the scope of the page is set up on the thesaurus
     */
    protected void applyThesaurus(ThesaurusSetup setup) {
        if (StringUtils.isEmpty(thesaurusUrl)) return;

        String previousUrl = savedThesaurusUrl;
        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(thesaurusUrl);
            setup.run(vocabulary);
            notifySaved();
            reloadUsages();
            askToRewire(previousUrl, thesaurusUrl);
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

    /** Whether a thesaurus is picked in the dialog, so it can be added. */
    public boolean isThesaurusPicked() {
        return pickedThesaurus != null && StringUtils.isNotBlank(pickedThesaurus.idTheso());
    }

    /** Registers the thesaurus picked in the dialog, then selects it. */
    public void addThesaurus() {
        if (!isThesaurusPicked()) return;
        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(pickedThesaurus.completeThesaurusUrl());
            loadOptions();
            thesaurusUrl = urlOf(vocabulary);
            pickedThesaurus = null;
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
