package fr.siamois.ui.bean.settings.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.form.FieldVocabularyConfigException;
import fr.siamois.domain.models.exceptions.form.NoVocabularySelectedException;
import fr.siamois.domain.models.exceptions.form.VocabularyConfigNotSavedException;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.settings.tableconfig.*;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.identifier.*;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.ConceptCollectionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.dto.entity.vocabulary.ConceptPrefLabelDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptAutocompleteDetachedDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptCollectionDetachedDTO;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.application.FacesMessage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.event.TabChangeEvent;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ProjectTableFieldSettingsBean implements Serializable {

    public static final int TAB_CHAMPS = 0;
    public static final int TAB_IDENTIFIANTS = 1;
    public static final String ID_UA = "ID_UA";
    public static final String NUM_UE = "NUM_UE";
    public static final String BRANCHE = "branche";
    public static final String COLLECTION = "collection";

    private final transient TableFieldConfigService tableFieldConfigService;
    private final transient FormConfigService formConfigService;
    private final transient ConceptService conceptService;
    private final transient ConceptCollectionService conceptCollectionService;
    private final transient VocabularyService vocabularyService;
    private final transient VocabularyMapper vocabularyMapper;
    private final transient LabelService labelService;
    private final LangBean langBean;
    private final transient IdentifierResolverRegistry identifierResolverRegistry;

    private ActionUnitDTO project;
    private List<ConfigurableTable> tables = new ArrayList<>();
    private ConfigurableTable selectedTable;
    private List<TypeSummary> typesForSelectedTable = new ArrayList<>();
    private String selectedTypeName;
    private int activeTabIndex = TAB_CHAMPS;

    private TypeFormConfig formConfig;
    private TypeFieldsConfig fieldsConfig;

    /**
     * The additional fields of {@link #fieldsConfig}, in display order — held as a mutable list
     * rather than derived on every call because the table they back is row-draggable: PrimeFaces
     * reorders the very list a draggable table reads while decoding the drag (see
     * {@code DraggableRowsFeature}), so a fresh list per call would drop the new order, and an
     * immutable one would make the decode throw.
     */
    private List<TypeFieldFormConfig> additionalFields = new ArrayList<>();

    private boolean pickerOpen;
    private String pickerQuery;

    private boolean drawerOpen;
    private String draftOriginalName;
    private String draftName;
    private FieldType draftType;
    private String draftDescription;

    private boolean draftIsSystem;
    private String draftFieldCode;
    private String draftSource;
    private String draftThesaurusUrl;
    private boolean draftConnectionTested;
    private transient ConceptAutocompleteDetachedDTO draftBrancheConcept;
    private String draftCollectionName;
    private transient VocabularyDTO draftVocabulary;

    /**
     * The results {@link #completeCollections} last returned to the autocomplete widget, kept around
     * so {@link #saveDrawer()} can resolve the DTO behind the label the user picked — unlike
     * {@link #draftBrancheConcept}, {@code draftCollectionName}'s {@code p:autoComplete} only
     * round-trips the label text, since {@link fr.siamois.dto.entity.vocabulary.ConceptCollectionDTO}
     * isn't {@code Serializable} and so can't be bound directly the way the branch concept is.
     */
    private transient List<ConceptCollectionDetachedDTO> lastCollectionResults = new ArrayList<>();

    /**
     * The external id of the branch concept / collection {@link #openDrawerForEdit} prefilled from the
     * field's existing configuration, if any — lets {@link #saveDraftVocabularyConfig()} tell "the user
     * left the prefilled value untouched" apart from "the user picked something new", so an unrelated
     * save (e.g. only the description changed) doesn't re-trigger a redundant remote thesaurus call.
     */
    private transient String draftOriginalBrancheConceptKey;
    private transient String draftOriginalCollectionKey;

    /**
     * The error shown in the drawer's own message area, at the top of the panel. Anything raised
     * while the drawer is open goes there rather than to the global growl: the growl is anchored to
     * the top-right corner of the page, exactly where the drawer sits, and renders under the
     * sidebar's overlay — so the user never sees it. Null when there is nothing to report.
     */
    private String drawerErrorMessage;

    private String newTypeName;

    private List<IdentifierSegment> identSegments = new ArrayList<>();
    private Integer identFirst;
    private Integer identLast;

    private static final Pattern IDENT_PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private static final int IDENT_DEFAULT_DIGITS = 3;

    public ProjectTableFieldSettingsBean(TableFieldConfigService tableFieldConfigService,
                                         FormConfigService formConfigService,
                                         ConceptService conceptService,
                                         ConceptCollectionService conceptCollectionService,
                                         VocabularyService vocabularyService,
                                         VocabularyMapper vocabularyMapper,
                                         LabelService labelService,
                                         LangBean langBean, IdentifierResolverRegistry identifierResolverRegistry) {
        this.tableFieldConfigService = tableFieldConfigService;
        this.formConfigService = formConfigService;
        this.conceptService = conceptService;
        this.conceptCollectionService = conceptCollectionService;
        this.vocabularyService = vocabularyService;
        this.vocabularyMapper = vocabularyMapper;
        this.labelService = labelService;
        this.langBean = langBean;
        this.identifierResolverRegistry = identifierResolverRegistry;
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        project = null;
        tables = new ArrayList<>();
        selectedTable = null;
        typesForSelectedTable = new ArrayList<>();
        selectedTypeName = null;
        activeTabIndex = TAB_CHAMPS;
        formConfig = null;
        setFieldsConfig(null);
        pickerOpen = false;
        pickerQuery = null;
        closeDrawer();
        newTypeName = null;
        identSegments = new ArrayList<>();
        identFirst = null;
        identLast = null;
    }

    /**
     * Caption to display for a field. A system field carries a message key rather than a caption,
     * whereas a field added from this screen carries what the user typed — same convention as
     * {@code SpatialUnitFieldBean#resolveCustomFieldLabel}.
     * <p>
     * The untranslated name stays the field's identity: it is what the service is called back with
     * to activate, require or delete it.
     */
    public String resolveFieldLabel(TypeFieldFormConfig field) {
        if (field.isSystemField()) {
            return langBean.msg(field.getName());
        }
        return field.getName();
    }

    public void init(ActionUnitDTO project) {
        reset();
        this.project = project;
        tables = tableFieldConfigService.listTables();
        if (!tables.isEmpty()) {
            selectTable(tables.get(0));
        }
    }

    public void selectTable(ConfigurableTable table) {
        selectedTable = table;
        typesForSelectedTable = tableFieldConfigService.listTypes(project.getId(), table);
        String firstNonDefault = typesForSelectedTable.stream()
                .filter(t -> !t.isDefault())
                .map(TypeSummary::getName)
                .findFirst()
                .orElse(typesForSelectedTable.isEmpty() ? null : typesForSelectedTable.get(0).getName());
        selectType(firstNonDefault);
    }

    public void selectType(String typeName) {
        selectedTypeName = typeName;
        if (typeName == null) {
            formConfig = null;
            setFieldsConfig(null);
            return;
        }
        loadConfigs();
    }

    private void loadConfigs() {
        formConfig = tableFieldConfigService.getFormConfig(project.getId(), selectedTable, selectedTypeName);
        setFieldsConfig(tableFieldConfigService.getFieldsConfig(project.getId(), selectedTable, selectedTypeName));
        loadIdentConfig();
    }

    public boolean isIdentTabAvailable() {
        return selectedTable != null && formConfig != null;
    }

    private void loadIdentConfig() {
        if (!isIdentTabAvailable()) {
            identSegments = new ArrayList<>();
            identFirst = null;
            identLast = null;
            return;
        }
        identFirst = formConfig.getMinCode();
        identLast = formConfig.getMaxCode();
        identSegments = parseFormat(formConfig.getIdentifierFormat());
    }

    /**
     * Reloads {@link #additionalFields} along, so the list the draggable table reads always mirrors
     * the configuration it is the additional-field view of.
     */
    public void setFieldsConfig(TypeFieldsConfig fieldsConfig) {
        this.fieldsConfig = fieldsConfig;
        this.additionalFields = fieldsConfig == null ? new ArrayList<>() : fieldsConfig.getFields().stream()
                .filter(f -> !f.isSystemField())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public boolean isSelectedTypeDefault() {
        return "_default".equals(selectedTypeName);
    }

    public long getHiddenSystemFieldCount() {
        if (fieldsConfig == null) return 0;
        return fieldsConfig.getFields().stream()
                .filter(f -> f.isSystemField() && !f.isActive() && !isPivotField(f))
                .count();
    }

    public List<TypeFieldFormConfig> getSystemFields() {
        if (fieldsConfig == null) return List.of();
        return fieldsConfig.getFields().stream()
                .filter(TypeFieldFormConfig::isSystemField)
                .filter(f -> !isPivotField(f))
                .toList();
    }

    /**
     * The table's own "type" field — the pivot whose value picks which {@link FormConfig} applies —
     * has no business being toggled active/mandatory or reordered here: it isn't an optional field of
     * the form, it's what selects the form. It stays a real, active system field of the table/entity
     * form ({@link TableFieldConfigService#getFieldsConfig} is untouched), only hidden from this
     * settings screen.
     */
    private boolean isPivotField(TypeFieldFormConfig field) {
        return selectedTable != null && selectedTable.getFieldCode().equals(field.getSourceLabel());
    }

    /**
     * Persists the order the user dropped the additional fields in. PrimeFaces has already applied
     * the move to {@link #additionalFields} by the time this runs — it reorders the list backing a
     * draggable table itself while decoding the drag — so the listener only reads the resulting
     * order off the list; the {@code ReorderEvent} indices carry nothing else it needs.
     */
    public void onAdditionalFieldReorder() {
        tableFieldConfigService.reorderAdditionalFields(project.getId(), selectedTable, selectedTypeName,
                additionalFields.stream().map(TypeFieldFormConfig::getName).toList());
    }

    public int getTypeCountFor(ConfigurableTable table) {
        return tableFieldConfigService.listTypes(project.getId(), table).size();
    }

    public void onTabChange(TabChangeEvent<?> event) {
        // activeTabIndex is bound directly via p:tabView activeIndex, nothing else to do here
    }

    /**
     * The p:toggleSwitch controls bind directly (two-way) to the row's boolean property, so by the
     * time these listeners fire, the row already carries its new value — we just persist it. A
     * field marked institutionLocked ignores the write (enforced server-side by the service too).
     */
    public void toggleFieldActive(TypeFieldFormConfig field) {
        tableFieldConfigService.setFieldActive(project.getId(), selectedTable, selectedTypeName, field.getName(), field.isActive());
    }

    public void toggleFieldMandatory(TypeFieldFormConfig field) {
        tableFieldConfigService.setFieldMandatory(project.getId(), selectedTable, selectedTypeName, field.getName(), field.isMandatory());
    }

    /**
     * Deletes an additional field, unless the project already holds answers for it — the service
     * then keeps the field and the user is told why the row is still there.
     */
    public void deleteAdditionalField(String fieldName) {
        boolean deleted = tableFieldConfigService.deleteAdditionalField(project.getId(), selectedTable, selectedTypeName, fieldName);
        if (!deleted) {
            MessageUtils.displayWarnMessage(langBean, "projectTables.champs.deleteRefusedInUse", fieldName);
            return;
        }
        loadConfigs();
    }

    public void addField() {
        openPicker();
    }

    public void openPicker() {
        pickerQuery = "";
        pickerOpen = true;
    }

    public void closePicker() {
        pickerOpen = false;
    }

    /**
     * The catalog is read against the selected type, which the picker adds the field to: a field
     * that type already carries is not offered. The dialog is part of the page, so this is called
     * on every render, including before a type is selected.
     */
    public List<FieldCatalogEntry> getFieldCatalog() {
        if (project == null || selectedTable == null || selectedTypeName == null) return List.of();
        return tableFieldConfigService.searchFieldCatalog(project.getId(), selectedTable, selectedTypeName, pickerQuery);
    }

    public void pickExistingField(FieldCatalogEntry entry) {
        tableFieldConfigService.addExistingField(project.getId(), selectedTable, selectedTypeName, entry.getName());
        pickerOpen = false;
        loadConfigs();
    }

    public void openNewFieldDrawer() {
        pickerOpen = false;
        openDrawerForCreate();
    }

    public void openDrawerForCreate() {
        draftOriginalName = null;
        draftName = "";
        draftType = FieldType.TEXT;
        draftDescription = "";
        draftIsSystem = false;
        resetDraftParams();
        drawerOpen = true;
    }

    public void openDrawerForEdit(TypeFieldFormConfig field) {
        draftOriginalName = field.getName();
        draftName = field.getName();
        draftType = field.getType();
        draftDescription = field.getDescription();
        draftIsSystem = field.isSystemField();
        draftFieldCode = field.getSourceLabel();
        resetDraftParams();
        draftSource = draftIsSystem && field.getType().isConfigurable() ? "principal" : null;
        if (isDraftConfigurable()) {
            prefillVocabularyConfig();
        }
        drawerOpen = true;
    }

    /**
     * Loads the field's current branch/collection restriction, if any, so the drawer shows what is
     * actually configured instead of always starting blank. Leaves the {@code "principal"} default
     * from {@link #openDrawerForEdit} untouched when the field carries no restriction.
     */
    private void prefillVocabularyConfig() {
        Optional<FormConfig> formConfigLocal = tableFieldConfigService.findFormConfig(project.getId(), selectedTable, selectedTypeName);
        Optional<CustomField> field = tableFieldConfigService.findField(project.getId(), selectedTable, selectedTypeName, draftName);
        if (formConfigLocal.isEmpty() || field.isEmpty() || !(field.get() instanceof CustomFieldConcept customFieldConcept)) {
            return;
        }
        Optional<ConceptFieldFormConfig> conceptConfig = formConfigService.findConceptConfigFor(formConfigLocal.get(), customFieldConcept);
        if (conceptConfig.isEmpty() || conceptConfig.get().isNotValid()) {
            return;
        }
        ConceptFieldFormConfig config = conceptConfig.get();
        if (config.isBranchConfig()) {
            prefillBranch(config.getBranchTopTerm());
        } else {
            prefillCollection(config.getCollection());
        }
    }

    private void prefillBranch(Concept branchTopTerm) {
        draftSource = BRANCHE;
        draftVocabulary = vocabularyMapper.convert(branchTopTerm.getVocabulary());
        draftThesaurusUrl = draftVocabulary.completeUri();
        draftConnectionTested = true;

        String label = labelService.findLabelOf(branchTopTerm, langBean.getLanguageCode()).getLabel();
        ConceptLabelDTO labelDTO = new ConceptPrefLabelDTO();
        labelDTO.setLabel(label);
        labelDTO.setConcept(ConceptDTO.builder()
                .externalId(branchTopTerm.getExternalId())
                .vocabulary(draftVocabulary)
                .deleted(false)
                .build());
        draftBrancheConcept = new ConceptAutocompleteDetachedDTO(labelDTO, label, List.of(), "", draftVocabulary.completeUri());
        draftOriginalBrancheConceptKey = branchTopTerm.getExternalId();
    }

    /**
     * {@link ConceptCollection} stores no label of its own (only its external id, vocabulary and
     * concepts), so the display label is resolved the same way {@link #completeCollections} does — a
     * live lookup against the thesaurus, matched back by external id.
     */
    private void prefillCollection(ConceptCollection collection) {
        draftSource = COLLECTION;
        draftVocabulary = vocabularyMapper.convert(collection.getVocabulary());
        draftThesaurusUrl = draftVocabulary.completeUri();
        draftConnectionTested = true;

        ConceptCollectionDetachedDTO match = conceptCollectionService.fetchCollectionsFromRemoteThesaurus(draftVocabulary).stream()
                .filter(c -> c.getExternalId().equals(collection.getExternalId()))
                .findFirst()
                .orElseGet(() -> new ConceptCollectionDetachedDTO(collection.getExternalId(), draftVocabulary, collection.getExternalId(), List.of()));
        lastCollectionResults = List.of(match);
        draftCollectionName = match.getLabelToDisplay();
        draftOriginalCollectionKey = match.getExternalId();
    }

    public void closeDrawer() {
        drawerOpen = false;
        draftOriginalName = null;
        draftName = null;
        draftType = null;
        draftDescription = null;
        draftIsSystem = false;
        draftFieldCode = null;
        resetDraftParams();
    }

    private void resetDraftParams() {
        draftSource = null;
        draftThesaurusUrl = null;
        draftConnectionTested = false;
        draftVocabulary = null;
        drawerErrorMessage = null;
        clearSourceSelection();
    }

    /**
     * Clears the branch/collection selection only, leaving the tested thesaurus connection
     * ({@link #draftThesaurusUrl}/{@link #draftVocabulary}/{@link #draftConnectionTested}) alone —
     * used by {@link #selectDraftSource} so switching between "branche" and "collection" doesn't force
     * retesting the connection to look something up in the same already-loaded thesaurus.
     */
    private void clearSourceSelection() {
        draftBrancheConcept = null;
        draftCollectionName = null;
        lastCollectionResults = new ArrayList<>();
        draftOriginalBrancheConceptKey = null;
        draftOriginalCollectionKey = null;
    }

    public boolean isDraftConfigurable() {
        return draftType != null && draftType.isConfigurable();
    }

    public void selectDraftSource(String source) {
        clearSourceSelection();
        draftSource = source;
    }

    /**
     * Backs the refresh button next to the thesaurus URL: resolves the typed URL against the real
     * thesaurus so the branch/collection pickers below can query it, following the same
     * resolve-then-report convention as {@code ProjectThesaurusSettingsBean#saveConfig}.
     */
    public void testThesaurusConnection() {
        drawerErrorMessage = null;
        if (draftThesaurusUrl == null || draftThesaurusUrl.isBlank()) {
            showDrawerError("projectTables.drawer.params.connectionMissingUrl");
            return;
        }
        try {
            String resolvedUrl = vocabularyService.resolveRedirections(draftThesaurusUrl);
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(resolvedUrl);
            draftVocabulary = vocabularyMapper.convert(vocabulary);
            // success needs no message of its own : the drawer shows the "✓ connected" line as soon
            // as the connection is marked as tested
            draftConnectionTested = true;
            preselectDesignatedConcept(resolvedUrl);
            draftThesaurusUrl = draftVocabulary.completeUri();
        } catch (InvalidEndpointException e) {
            log.error("Invalid thesaurus URL '{}'", draftThesaurusUrl, e);
            draftConnectionTested = false;
            draftVocabulary = null;
            showDrawerError("myProfile.thesaurus.uri.invalid");
        } catch (RuntimeException e) {
            // anything the thesaurus lookup didn't turn into an InvalidEndpointException would
            // otherwise leave the button silent, which reads as "nothing happened"
            log.error("Could not reach the thesaurus at '{}'", draftThesaurusUrl, e);
            draftConnectionTested = false;
            draftVocabulary = null;
            showDrawerError("projectTables.drawer.params.connectionFailed");
        }
    }

    private void preselectDesignatedConcept(String resolvedUrl) {
        Optional<ConceptAutocompleteDetachedDTO> concept = conceptService.fetchConceptDesignatedBy(draftVocabulary, resolvedUrl);
        if (concept.isPresent()) {
            draftSource = BRANCHE;
            draftBrancheConcept = concept.get();
            draftCollectionName = null;
            draftOriginalBrancheConceptKey = null;
            return;
        }
        conceptCollectionService.fetchCollectionDesignatedBy(draftVocabulary, resolvedUrl).ifPresent(collection -> {
            draftSource = COLLECTION;
            lastCollectionResults = List.of(collection);
            draftCollectionName = collection.getLabelToDisplay();
            draftBrancheConcept = null;
            draftOriginalCollectionKey = null;
        });
    }

    /**
     * Fills the drawer's message area. Kept apart from {@link MessageUtils} on purpose: these
     * messages belong to the panel the user is looking at, not to the page-wide growl.
     */
    private void showDrawerError(String messageCode, Object... args) {
        drawerErrorMessage = langBean.msg(messageCode, args);
    }

    /**
     * The OpenTheso URL of the whole thesaurus currently loaded, for the "view" button next to the
     * load/refresh button — null (and the button disabled) until {@link #testThesaurusConnection()}
     * has resolved one.
     */
    public String getDraftVocabularyUrl() {
        return draftVocabulary == null ? null : draftVocabulary.completeUri();
    }

    public boolean isDraftBrancheConceptSelected() {
        return draftBrancheConcept != null && draftBrancheConcept.concept() != null;
    }

    /**
     * The OpenTheso URL of the branch's root concept, for the "view" button next to the branch picker —
     * null (and the button disabled) until a concept has been picked.
     */
    public String getDraftBrancheConceptUrl() {
        if (!isDraftBrancheConceptSelected()) {
            return null;
        }
        ConceptDTO concept = draftBrancheConcept.concept();
        VocabularyDTO vocabulary = concept.getVocabulary();
        return vocabulary.getBaseUri() + "/?idc=" + concept.getExternalId() + "&idt=" + vocabulary.getExternalVocabularyId();
    }

    public boolean isDraftCollectionSelected() {
        return selectedCollection().isPresent();
    }

    /**
     * The OpenTheso URL of the selected collection, for the "view" button next to the collection picker
     * — null (and the button disabled) until the typed label matches one of {@link #lastCollectionResults}.
     */
    public String getDraftCollectionUrl() {
        return selectedCollection()
                .map(c -> c.getVocabulary().getBaseUri() + "/?idg=" + c.getExternalId() + "&idt=" + c.getVocabulary().getExternalVocabularyId())
                .orElse(null);
    }

    private Optional<ConceptCollectionDetachedDTO> selectedCollection() {
        return lastCollectionResults.stream()
                .filter(c -> c.getLabelToDisplay().equals(draftCollectionName))
                .findFirst();
    }

    /**
     * Empty until the thesaurus connection has been tested successfully. A thesaurus that answers
     * with something unparseable, or that stopped answering since the connection was tested, must
     * not surface as "no result" : the user would read it as "this concept doesn't exist".
     */
    public List<ConceptAutocompleteDetachedDTO> completeBrancheConcepts(String query) {
        if (draftVocabulary == null) {
            return List.of();
        }
        try {
            return conceptService.fetchAutocompleteFromRemoteThesaurus(draftVocabulary, query);
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Could not fetch the concepts matching '{}' in thesaurus {}", query, draftVocabulary.getBaseUri(), e);
            showDrawerError("common.error.internal");
            return List.of();
        }
    }

    /**
     * Empty until the thesaurus connection has been tested successfully. Caches its results in
     * {@link #lastCollectionResults} so {@link #saveDrawer()} can resolve the collection behind the
     * label the {@code p:autoComplete} round-trips. Reports a failing thesaurus like
     * {@link #completeBrancheConcepts} does, and for the same reason.
     */
    public List<String> completeCollections(String query) {
        if (draftVocabulary == null) {
            lastCollectionResults = List.of();
            return List.of();
        }
        try {
            lastCollectionResults = conceptCollectionService.fetchCollectionsFromRemoteThesaurus(draftVocabulary).stream()
                    .filter(c -> query == null || query.isBlank() || c.getLabelToDisplay().toLowerCase().contains(query.toLowerCase()))
                    .toList();
        } catch (RuntimeException e) {
            log.error("Could not fetch the collections of thesaurus {}", draftVocabulary.getBaseUri(), e);
            lastCollectionResults = List.of();
            showDrawerError("common.error.internal");
        }
        return lastCollectionResults.stream().map(ConceptCollectionDetachedDTO::getLabelToDisplay).toList();
    }

    /**
     * The field itself is saved first and stays saved whatever happens next : only the vocabulary
     * configuration can fail here, and the drawer must then say so instead of closing on a success
     * message the configuration never got.
     */
    public void saveDrawer() {
        drawerErrorMessage = null;
        if (draftOriginalName == null) {
            tableFieldConfigService.createField(project.getId(), selectedTable, selectedTypeName, draftName, draftType, draftDescription);
        } else {
            tableFieldConfigService.updateField(project.getId(), selectedTable, selectedTypeName, draftOriginalName, draftName, draftType, draftDescription);
        }
        // the field now exists under that name : should the vocabulary configuration fail below, the
        // drawer stays open on the error, and saving again must update that field rather than try to
        // create a second one — or, after a rename, update from a name that no longer exists
        draftOriginalName = draftName;

        try {
            saveDraftVocabularyConfig();
        } catch (FieldVocabularyConfigException e) {
            log.error("Vocabulary configuration of field '{}' on type '{}' of table {} was not saved",
                    draftName, selectedTypeName, selectedTable, e);
            showDrawerError(e.getMessageCode());
            // the drawer stays open : the message lives in it, and the user is one correction away
            // from a working configuration
            loadConfigs();
            return;
        }

        loadConfigs();
        closeDrawer();
        MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "projectTables.drawer.saveSuccess");
    }

    /**
     * Persists the vocabulary source picked in the drawer onto the field just saved by
     * {@link #saveDrawer()}: clears any existing branch/collection restriction for
     * {@code "principal"}, or saves the newly picked branch/collection — a no-op when the source is
     * unset (non-configurable fields) or when the user left the prefilled selection untouched.
     *
     * @throws FieldVocabularyConfigException when the picked branch/collection did not make it onto
     * the field, carrying the message {@link #saveDrawer()} reports
     */
    private void saveDraftVocabularyConfig() {
        if ("principal".equals(draftSource)) {
            clearConceptConfigIfAny();
        } else if (BRANCHE.equals(draftSource)) {
            saveBrancheConceptIfChanged();
        } else if (COLLECTION.equals(draftSource)) {
            saveCollectionIfChanged();
        }
    }

    /**
     * No-op when the field never had a {@link FormConfig}: nothing was ever configured, so there is
     * nothing to clear, and materializing one just for this would be pointless.
     */
    private void clearConceptConfigIfAny() {
        tableFieldConfigService.findFormConfig(project.getId(), selectedTable, selectedTypeName)
                .ifPresent(formConfigLocal -> tableFieldConfigService.findField(project.getId(), selectedTable, selectedTypeName, draftName)
                        .filter(CustomFieldConcept.class::isInstance)
                        .map(CustomFieldConcept.class::cast)
                        .ifPresent(customFieldConcept -> formConfigService.clearConceptConfigFor(formConfigLocal, customFieldConcept)));
    }

    private void saveBrancheConceptIfChanged() {
        // the picker has forceSelection, so an empty value means the user asked for a branch without
        // ever picking one : the field keeps no vocabulary, which is a failed configuration
        if (draftBrancheConcept == null || draftBrancheConcept.concept() == null) {
            throw new NoVocabularySelectedException(
                    String.format("No branch picked for field '%s'", draftName),
                    "projectTables.drawer.params.brancheMissing");
        }
        if (draftBrancheConcept.concept().getExternalId().equals(draftOriginalBrancheConceptKey)) {
            return;
        }
        ConceptFieldTarget target = resolveConceptFieldTarget("projectTables.drawer.params.brancheSaveError");
        try {
            formConfigService.addConceptConfigFor(target.formConfig(), target.field(), Objects.requireNonNull(draftBrancheConcept.concept()));
        } catch (RuntimeException e) {
            throw new VocabularyConfigNotSavedException(
                    String.format("Could not save the branch '%s' as the vocabulary of field '%s'",
                            draftBrancheConcept.concept().getExternalId(), draftName),
                    "projectTables.drawer.params.brancheSaveError", e);
        }
    }

    private void saveCollectionIfChanged() {
        Optional<ConceptCollectionDetachedDTO> selected = selectedCollection();
        // unlike the branch picker this one accepts free text : an empty field, or a label matching no
        // collection of the thesaurus, leaves nothing to configure
        if (selected.isEmpty()) {
            throw new NoVocabularySelectedException(
                    String.format("No collection of the thesaurus matches '%s' for field '%s'", draftCollectionName, draftName),
                    "projectTables.drawer.params.collectionMissing");
        }
        if (selected.get().getExternalId().equals(draftOriginalCollectionKey)) {
            return;
        }
        ConceptFieldTarget target = resolveConceptFieldTarget("projectTables.drawer.params.collectionSaveError");
        try {
            formConfigService.addConceptConfigFor(target.formConfig(), target.field(), selected.get());
        } catch (RuntimeException e) {
            throw new VocabularyConfigNotSavedException(
                    String.format("Could not save the collection '%s' as the vocabulary of field '%s'",
                            selected.get().getExternalId(), draftName),
                    "projectTables.drawer.params.collectionSaveError", e);
        }
    }

    private record ConceptFieldTarget(FormConfig formConfig, CustomFieldConcept field) {
    }

    /**
     * The field row was just written, so failing to find it back as a concept field leaves the picked
     * branch/collection nowhere to go — a failed configuration like any other.
     *
     * @param messageCode the message to report for that field, branch or collection flavoured
     */
    private ConceptFieldTarget resolveConceptFieldTarget(String messageCode) {
        Optional<FormConfig> formConfigLocal = tableFieldConfigService.createOrGetFormConfig(project.getId(), selectedTable, selectedTypeName);
        Optional<CustomField> field = tableFieldConfigService.findField(project.getId(), selectedTable, selectedTypeName, draftName);
        if (formConfigLocal.isEmpty() || field.isEmpty() || !(field.get() instanceof CustomFieldConcept customFieldConcept)) {
            throw new VocabularyConfigNotSavedException(
                    String.format("Could not resolve the saved field '%s' as a concept field on type '%s' of table %s",
                            draftName, selectedTypeName, selectedTable),
                    messageCode);
        }
        return new ConceptFieldTarget(formConfigLocal.get(), customFieldConcept);
    }

    public boolean isDraftCreateMode() {
        return draftOriginalName == null;
    }

    public FieldType[] getFieldTypeOptions() {
        return new FieldType[]{FieldType.TEXT, FieldType.INTEGER, FieldType.MEASUREMENT, FieldType.SELECT_ONE, FieldType.SELECT_MULTIPLE};
    }

    /**
     * The {@code p:selectOneMenu} binds to this String-backed pair rather than {@code draftType}
     * directly: every other {@code p:selectOneMenu} in this codebase does the same (see
     * {@code ProfileSettingsBean.FDefaultInstitutionId}/{@code FSelectedLang}), because relying on
     * JSF's implicit enum converter here doesn't reliably round-trip the selection on postback.
     */
    public String getDraftTypeName() {
        return draftType == null ? null : draftType.name();
    }

    public void setDraftTypeName(String name) {
        draftType = name == null ? null : FieldType.valueOf(name);
    }

    public List<String> completeConfigurableTypes(String query) {
        return tableFieldConfigService.listConfigurableTypes(project.getId(), selectedTable, query);
    }

    public void addConfiguration() {
        tableFieldConfigService.addConfiguration(project.getId(), selectedTable, newTypeName);
        typesForSelectedTable = tableFieldConfigService.listTypes(project.getId(), selectedTable);
        selectType(newTypeName);
        MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "projectTables.tree.newConfigSuccess", newTypeName);
        newTypeName = null;
    }

    // ===== Identifiants =====

    private List<IdentifierSegment> parseFormat(String format) {
        List<IdentifierSegment> segments = new ArrayList<>();
        if (format == null) return segments;

        Matcher matcher = IDENT_PLACEHOLDER_PATTERN.matcher(format);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                segments.add(textSegment(format.substring(last, matcher.start())));
            }
            String[] parts = matcher.group(1).split(":", 2);
            String code = parts[0];
            boolean numeric = resolver(code).map(r -> r.valueKind() == IdentifierValueKind.NUMERICAL).orElse(false);
            int digits = parts.length > 1 ? parts[1].length() : 0;
            segments.add(tokenSegment(code, numeric, digits));
            last = matcher.end();
        }
        if (last < format.length()) {
            segments.add(textSegment(format.substring(last)));
        }
        return segments;
    }

    private String serializeFormat(List<IdentifierSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (IdentifierSegment seg : segments) {
            if (!seg.isToken()) {
                sb.append(seg.getText() == null ? "" : seg.getText());
                continue;
            }
            sb.append('{').append(seg.getCode());
            if (seg.isNumeric() && seg.getDigits() > 0) {
                sb.append(':').append("0".repeat(seg.getDigits()));
            }
            sb.append('}');
        }
        return sb.toString();
    }

    private IdentifierSegment textSegment(String text) {
        return IdentifierSegment.builder().token(false).text(text).build();
    }

    private IdentifierSegment tokenSegment(String code, boolean numeric, int digits) {
        IdentifierResolver<IdentifierRenderContext> resolver = resolver(code).orElse(null);
        String label = resolver == null ? code : langBean.msg(resolver.titleCode());
        return IdentifierSegment.builder().token(true).code(code).label(label).numeric(numeric).digits(digits).build();
    }

    public void addIdentTextSegment() {
        identSegments.add(textSegment(""));
    }

    public void addIdentTokenSegment(String code) {
        boolean numeric = resolver(code).map(r -> r.valueKind() == IdentifierValueKind.NUMERICAL).orElse(false);
        identSegments.add(tokenSegment(code, numeric, IDENT_DEFAULT_DIGITS));
    }

    public void moveIdentSegmentLeft(int index) {
        if (index > 0) Collections.swap(identSegments, index, index - 1);
    }

    public void moveIdentSegmentRight(int index) {
        if (index < identSegments.size() - 1) Collections.swap(identSegments, index, index + 1);
    }

    public void removeIdentSegment(int index) {
        identSegments.remove(index);
    }

    public List<IdentifierResolver<IdentifierRenderContext>> getIdentifierResolvers() {
        return selectedTable == null ? List.of() : identifierResolverRegistry.resolvers(selectedTable);
    }

    public String getIdentExample() {
        if (!isIdentTabAvailable()) return "";
        String format = serializeFormat(identSegments);
        try {
            return identifierResolverRegistry.render(selectedTable, format, previewContext());
        } catch (IllegalArgumentException exception) {
            return format;
        }
    }

    private IdentifierRenderContext previewContext() {
        Map<String, Object> values = new HashMap<>();
        values.put(identifierResolverRegistry.ownNumericalToken(selectedTable), switch (selectedTable) {
            case UE -> 142;
            case MOBILIER -> 27;
            case CONTENANT -> 8;
            case PHASE -> 2;
        });
        values.put(ID_UA, "UA1");
        return new MapIdentifierRenderContext(values);
    }

    public void saveIdentConfig() {
        if (!isIdentTabAvailable()) return;

        String format = serializeFormat(identSegments);
        if (identifierFormatIsInvalid(format)) return;

        formConfig.setIdentifierFormat(format);
        formConfig.setMinCode(identFirst);
        formConfig.setMaxCode(identLast);
        tableFieldConfigService.saveFormConfig(project.getId(), selectedTable, formConfig);
        loadConfigs();

        MessageUtils.displayInfoMessage(langBean, "actionUnit.settings.success.identifierConfigSaved");
    }

    private boolean identifierFormatIsInvalid(String format) {
        if (identFirst == null || identLast == null || identFirst < 0 || identLast < identFirst) {
            MessageUtils.displayErrorMessage(langBean, "projectTables.ident.invalidRange");
            return true;
        }
        try {
            identifierResolverRegistry.validate(selectedTable, format);
        } catch (IdentifierFormatException exception) {
            MessageUtils.displayErrorMessage(langBean, "actionUnit.settings.error.invalidIdentifierFormat");
            return true;
        }
        String ownToken = identifierResolverRegistry.ownNumericalToken(selectedTable);
        for (IdentifierSegment segment : identSegments) {
            if (segment.isToken() && ownToken.equals(segment.getCode()) && segment.getDigits() > 0
                    && String.valueOf(identLast).length() > segment.getDigits()) {
                MessageUtils.displayErrorMessage(langBean, "actionUnit.settings.error.insufficientDigits", ownToken);
                return true;
            }
        }
        return false;
    }

    private Optional<IdentifierResolver<IdentifierRenderContext>> resolver(String code) {
        return selectedTable == null ? Optional.empty() : identifierResolverRegistry.resolver(selectedTable, code);
    }

}
