package fr.siamois.ui.bean.settings.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.settings.ProjectCodeSource;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ThesaurusInfo;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.CodeSourceService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.dto.entity.vocabulary.ConceptPrefLabelDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptAutocompleteDetachedDTO;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import fr.siamois.ui.bean.LangBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where a project reads the types of a table, on the table's page. A table's types are the values of a field no form
 * shows, so their source is set here rather than in a field's drawer, but the same way: a code looked up in a thesaurus,
 * or a branch of it, the default being where the type code is read by default (the SIAMOIS thesaurus).
 */
@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ProjectTableTypeSourceBean implements Serializable {

    public static final String FIELD_CODE = "code";
    public static final String BRANCH = "branche";
    /** The OpenTheso instance offered when no thesaurus is known yet. */
    private static final String DEFAULT_THESAURUS_SERVER = "https://thesaurus.mom.fr";

    private final transient CodeSourceService codeSourceService;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final transient VocabularyService vocabularyService;
    private final transient VocabularyMapper vocabularyMapper;
    private final transient ConceptService conceptService;
    private final transient LabelService labelService;
    private final LangBean langBean;

    private ActionUnitDTO project;
    private ConfigurableTable table;

    /** {@code null}: the type code is read where it is by default. */
    private String source;
    private String thesaurusUrl;
    private String serverUrl;
    private transient List<String> serverOptions;
    private transient ThesaurusInfo thesaurus;
    private transient List<ThesaurusInfo> thesaurusOptions = new ArrayList<>();
    private boolean connectionTested;
    private transient VocabularyDTO vocabulary;

    /** Where the type code is read by default, shown (not editable) next to the other sources. */
    private transient VocabularyDTO defaultVocabulary;
    private String defaultThesaurusLabel;
    private String defaultCode;
    private transient List<String> codeOptions = new ArrayList<>();
    private String codeChoice;
    private transient ConceptAutocompleteDetachedDTO branchConcept;
    private String errorMessage;
    private String savedMessage;

    @Autowired
    public ProjectTableTypeSourceBean(CodeSourceService codeSourceService, FieldConfigurationService fieldConfigurationService,
                                      VocabularyService vocabularyService, VocabularyMapper vocabularyMapper,
                                      ConceptService conceptService, LabelService labelService, LangBean langBean) {
        this.codeSourceService = codeSourceService;
        this.fieldConfigurationService = fieldConfigurationService;
        this.vocabularyService = vocabularyService;
        this.vocabularyMapper = vocabularyMapper;
        this.conceptService = conceptService;
        this.labelService = labelService;
        this.langBean = langBean;
    }

    /** Opens the page of a table: shows what its types are read from now. */
    public void open(ActionUnitDTO project, ConfigurableTable table) {
        boolean sameTable = this.project != null && this.table == table
                && java.util.Objects.equals(this.project.getId(), project.getId());
        this.project = project;
        this.table = table;
        resetDraft();
        if (!sameTable) {
            savedMessage = null;
        }
        refreshDefaultSource();
        codeSourceService.find(project.getId(), table.getFieldCode()).ifPresent(this::prefill);
    }

    private void refreshDefaultSource() {
        defaultVocabulary = null;
        defaultThesaurusLabel = null;
        defaultCode = table.getFieldCode();
        if (project.getCreatedByInstitution() == null) {
            return;
        }
        fieldConfigurationService
                .findDefaultVocabularyOfFieldCode(project.getCreatedByInstitution().getId(), project.getId(), table.getFieldCode())
                .ifPresent(found -> {
                    defaultVocabulary = vocabularyMapper.convert(found);
                    defaultThesaurusLabel = labelOfThesaurus(found);
                });
    }

    private String labelOfThesaurus(Vocabulary found) {
        try {
            String label = labelService.findLabelOf(found, langBean.getLanguageCode()).getValue();
            return StringUtils.isBlank(label) ? found.getExternalVocabularyId() : label;
        } catch (RuntimeException e) {
            return found.getExternalVocabularyId();
        }
    }

    public String getDefaultServerUrl() {
        return defaultVocabulary == null ? null : normalizeServer(defaultVocabulary.getBaseUri());
    }

    public String getDefaultThesaurusUrl() {
        return defaultVocabulary == null ? null : defaultVocabulary.completeUri();
    }

    private void prefill(ProjectCodeSource current) {
        if (current.isBranch()) {
            Concept top = current.getBranchTopTerm();
            source = BRANCH;
            vocabulary = vocabularyMapper.convert(top.getVocabulary());
            connected();
            branchConcept = branchOf(top);
        } else if (current.getVocabulary() != null) {
            Vocabulary sourceVocabulary = current.getVocabulary();
            source = FIELD_CODE;
            vocabulary = vocabularyMapper.convert(sourceVocabulary);
            connected();
            loadCodeOptions();
            codeChoice = current.getSourceFieldCode();
        }
    }

    private void connected() {
        thesaurusUrl = vocabulary.completeUri();
        connectionTested = true;
        selectServerOf(vocabulary);
    }

    private ConceptAutocompleteDetachedDTO branchOf(Concept top) {
        String label = labelService.findLabelOf(top, langBean.getLanguageCode()).getLabel();
        ConceptLabelDTO labelDTO = new ConceptPrefLabelDTO();
        labelDTO.setLabel(label);
        labelDTO.setConcept(ConceptDTO.builder()
                .externalId(top.getExternalId())
                .vocabulary(vocabulary)
                .deleted(false)
                .build());
        return new ConceptAutocompleteDetachedDTO(labelDTO, label, List.of(), "", vocabulary.completeUri());
    }

    private void resetDraft() {
        source = null;
        thesaurusUrl = null;
        serverUrl = null;
        thesaurus = null;
        thesaurusOptions = new ArrayList<>();
        connectionTested = false;
        vocabulary = null;
        clearSelection();
        errorMessage = null;
    }

    private void clearSelection() {
        codeOptions = new ArrayList<>();
        codeChoice = null;
        branchConcept = null;
    }

    /** The instances to pick from: those of the thesauri the database knows, plus the default one. */
    public List<String> getServerOptions() {
        if (serverOptions == null) {
            Set<String> servers = new LinkedHashSet<>();
            servers.add(DEFAULT_THESAURUS_SERVER);
            vocabularyService.findAll().forEach(v -> servers.add(normalizeServer(v.getBaseUri())));
            serverOptions = new ArrayList<>(servers);
        }
        return serverOptions;
    }

    private static String normalizeServer(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** The instances matching what was typed. */
    public List<String> completeServers(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        return getServerOptions().stream().filter(server -> server.toLowerCase().contains(needle)).toList();
    }

    /** The thesauri of the picked instance matching what was typed, by label or identifier. */
    public List<ThesaurusInfo> completeThesauri(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        return thesaurusOptions.stream()
                .filter(info -> info.label().toLowerCase().contains(needle) || info.idTheso().toLowerCase().contains(needle))
                .toList();
    }

    /** Another instance was picked: its thesauri are listed, and nothing is loaded until one is picked. */
    public void onServerChange() {
        errorMessage = null;
        thesaurus = null;
        thesaurusUrl = null;
        connectionTested = false;
        vocabulary = null;
        clearSelection();
        if (!loadThesaurusOptions()) {
            error("projectTables.drawer.params.serverUnreachable");
        }
    }

    /** A thesaurus of the instance was picked: it is loaded so its codes and branches can be picked. */
    public void onThesaurusChange() {
        clearSelection();
        connectionTested = false;
        vocabulary = null;
        thesaurusUrl = thesaurus == null || thesaurus.idTheso().isBlank() ? null : thesaurus.completeThesaurusUrl();
        if (thesaurusUrl != null) {
            testConnection();
        }
    }

    private void selectServerOf(VocabularyDTO loaded) {
        serverUrl = normalizeServer(loaded.getBaseUri());
        thesaurus = null;
        if (!getServerOptions().contains(serverUrl)) {
            serverOptions.add(serverUrl);
        }
        loadThesaurusOptions();
        thesaurus = thesaurusOptions.stream()
                .filter(info -> info.idTheso().equals(loaded.getExternalVocabularyId()))
                .findFirst()
                .orElse(null);
    }

    private boolean loadThesaurusOptions() {
        thesaurusOptions = new ArrayList<>();
        if (serverUrl == null || serverUrl.isBlank()) {
            return true;
        }
        try {
            thesaurusOptions = new ArrayList<>(vocabularyService.findAllThesaurusOf(serverUrl, langBean.getLanguageCode()));
            return true;
        } catch (InvalidEndpointException e) {
            log.error("Could not list the thesauri of {}", serverUrl, e);
            return false;
        }
    }

    /** Whether the table has a type source to set: only a table whose types are read by a code has one. */
    public boolean isAvailable() {
        return project != null && table != null && table.getFieldCode() != null;
    }

    public boolean isCustom() {
        return isAvailable() && codeSourceService.find(project.getId(), table.getFieldCode()).isPresent();
    }

    public void select(String newSource) {
        errorMessage = null;
        savedMessage = null;
        if (newSource == null) {
            resetDraft();
            return;
        }
        clearSelection();
        source = newSource;
        offerDocumentationThesaurus();
        source = newSource;
        if (FIELD_CODE.equals(newSource)) {
            loadCodeOptions();
        }
    }

    /** With no thesaurus yet, starts from the one the project documents its fields and types in, then the institution's. */
    private void offerDocumentationThesaurus() {
        if (thesaurusUrl != null && !thesaurusUrl.isBlank()) {
            return;
        }
        Optional<String> url = fieldConfigurationService.findVocabularyUrlOfActionUnitId(project.getId());
        if (url.isEmpty() && project.getCreatedByInstitution() != null) {
            url = fieldConfigurationService.findVocabularyUrlOfInstitutionId(project.getCreatedByInstitution().getId());
        }
        url.ifPresent(found -> {
            thesaurusUrl = found;
            testConnection();
        });
    }

    /** Loads the thesaurus whose address was typed, so its codes and branches can be picked. */
    public void testConnection() {
        errorMessage = null;
        if (thesaurusUrl == null || thesaurusUrl.isBlank()) {
            error("projectTables.drawer.params.connectionMissingUrl");
            return;
        }
        try {
            Vocabulary loaded = vocabularyService.findOrCreateVocabularyOfUri(vocabularyService.resolveRedirections(thesaurusUrl));
            vocabulary = vocabularyMapper.convert(loaded);
            connected();
            if (FIELD_CODE.equals(source)) {
                loadCodeOptions();
            }
        } catch (InvalidEndpointException e) {
            log.error("Invalid thesaurus URL '{}'", thesaurusUrl, e);
            connectionTested = false;
            vocabulary = null;
            error("myProfile.thesaurus.uri.invalid");
        } catch (RuntimeException e) {
            log.error("Could not reach the thesaurus at '{}'", thesaurusUrl, e);
            connectionTested = false;
            vocabulary = null;
            error("projectTables.drawer.params.connectionFailed");
        }
    }

    private void loadCodeOptions() {
        codeOptions = new ArrayList<>();
        if (vocabulary == null || !connectionTested) {
            return;
        }
        try {
            Vocabulary loaded = vocabularyService.findVocabularyById(vocabulary.getId());
            codeOptions = new ArrayList<>(fieldConfigurationService.findFieldCodesOf(loaded));
            if (codeOptions.isEmpty()) {
                error("projectTables.drawer.params.codeNone");
            }
        } catch (NotSiamoisThesaurusException e) {
            error("myProfile.thesaurus.siamois.invalid");
        } catch (ErrorProcessingExpansionException | RuntimeException e) {
            log.error("Could not read the field codes of thesaurus {}", vocabulary.getExternalVocabularyId(), e);
            error("thesaurus.error.processingExpansion");
        }
    }

    public List<ConceptAutocompleteDetachedDTO> completeBranches(String query) {
        if (vocabulary == null) {
            return List.of();
        }
        try {
            return conceptService.fetchAutocompleteFromRemoteThesaurus(vocabulary, query);
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Could not fetch the concepts matching '{}' in thesaurus {}", query, vocabulary.getBaseUri(), e);
            error("common.error.internal");
            return List.of();
        }
    }

    public boolean isBranchSelected() {
        return branchConcept != null && branchConcept.concept() != null;
    }

    /** Applies what was picked, "default" included. Returns whether the types' source changed, so the page can list them again. */
    public boolean save() {
        errorMessage = null;
        savedMessage = null;
        if (!isAvailable()) {
            return false;
        }
        if (source == null) {
            // "default" applied: the types are read where they are by default again
            return isCustom() && useDefault();
        }
        try {
            if (FIELD_CODE.equals(source)) {
                if (codeChoice == null || codeChoice.isBlank() || vocabulary == null) {
                    error("projectTables.typeSource.codeMissing");
                    return false;
                }
                codeSourceService.useFieldCode(project.getId(), table.getFieldCode(),
                        vocabularyService.findVocabularyById(vocabulary.getId()), codeChoice);
            } else {
                if (!isBranchSelected()) {
                    error("projectTables.typeSource.brancheMissing");
                    return false;
                }
                codeSourceService.useBranch(project.getId(), table.getFieldCode(), branchConcept.concept(), new ProgressWrapper());
            }
            savedMessage = langBean.msg("projectTables.typeSource.saved");
            return true;
        } catch (InvalidEndpointException | RuntimeException e) {
            log.error("Could not save the source of the types of table {}", table, e);
            error("projectTables.typeSource.saveError");
            return false;
        }
    }

    /** Backs the save button: tells the page whether the types must be listed again. */
    public void saveAction() {
        org.primefaces.PrimeFaces.current().ajax().addCallbackParam("changed", save());
    }

    /** Backs the "default" button: tells the page whether the types must be listed again. */
    public void useDefaultAction() {
        org.primefaces.PrimeFaces.current().ajax().addCallbackParam("changed", useDefault());
    }

    /** Reads the types where they are read by default again. Returns whether it changed. */
    public boolean useDefault() {
        errorMessage = null;
        if (!isAvailable()) {
            return false;
        }
        codeSourceService.clear(project.getId(), table.getFieldCode());
        resetDraft();
        savedMessage = langBean.msg("projectTables.typeSource.reset");
        return true;
    }

    private void error(String messageCode) {
        errorMessage = langBean.msg(messageCode);
    }
}
