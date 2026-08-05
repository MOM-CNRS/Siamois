package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.settings.tableconfig.*;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuIdentifierResolver;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.form.FormConfigService;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Optional;
import java.util.stream.Collectors;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;

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

    private final transient TableFieldConfigService tableFieldConfigService;
    private final transient FormConfigService formConfigService;
    private final transient ConceptService conceptService;
    private final transient ConceptCollectionService conceptCollectionService;
    private final transient VocabularyService vocabularyService;
    private final transient VocabularyMapper vocabularyMapper;
    private final transient LabelService labelService;
    private final LangBean langBean;
    private final transient RecordingUnitService recordingUnitService;
    private final transient ActionUnitService actionUnitService;

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
                                         LangBean langBean, RecordingUnitService recordingUnitService, ActionUnitService actionUnitService) {
        this.tableFieldConfigService = tableFieldConfigService;
        this.formConfigService = formConfigService;
        this.conceptService = conceptService;
        this.conceptCollectionService = conceptCollectionService;
        this.vocabularyService = vocabularyService;
        this.vocabularyMapper = vocabularyMapper;
        this.labelService = labelService;
        this.langBean = langBean;
        this.recordingUnitService = recordingUnitService;
        this.actionUnitService = actionUnitService;
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

    /**
     * The UE identifier format is project-wide (one format governs every RecordingUnit in the
     * project), so it only makes sense to surface it when the UE / _default node is selected —
     * other table/type combinations show an "unavailable" placeholder instead.
     */
    public boolean isIdentTabAvailable() {
        return selectedTable == ConfigurableTable.UE && isSelectedTypeDefault();
    }

    private void loadIdentConfig() {
        if (!isIdentTabAvailable()) {
            identSegments = new ArrayList<>();
            identFirst = null;
            identLast = null;
            return;
        }
        identFirst = project.getMinRecordingUnitCode();
        identLast = project.getMaxRecordingUnitCode();
        identSegments = parseFormat(project.getRecordingUnitIdentifierFormat());
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
        return fieldsConfig.getFields().stream().filter(f -> f.isSystemField() && !f.isActive()).count();
    }

    public List<TypeFieldFormConfig> getSystemFields() {
        if (fieldsConfig == null) return List.of();
        return fieldsConfig.getFields().stream().filter(TypeFieldFormConfig::isSystemField).toList();
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
        draftSource = "branche";
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
        draftSource = "collection";
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
        if (draftThesaurusUrl == null || draftThesaurusUrl.isBlank()) {
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_WARN, "projectTables.drawer.params.connectionMissingUrl");
            return;
        }
        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(draftThesaurusUrl);
            draftVocabulary = vocabularyMapper.convert(vocabulary);
            draftConnectionTested = true;
            MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "projectTables.drawer.params.connectionOk");
        } catch (InvalidEndpointException e) {
            displayErrorMessage(langBean, "myProfile.thesaurus.uri.invalid");
        }
    }

    /** Empty until the thesaurus connection has been tested successfully. */
    public List<ConceptAutocompleteDetachedDTO> completeBrancheConcepts(String query) {
        if (draftVocabulary == null) {
            return List.of();
        }
        return conceptService.fetchAutocompleteFromRemoteThesaurus(draftVocabulary, query);
    }

    /**
     * Empty until the thesaurus connection has been tested successfully. Caches its results in
     * {@link #lastCollectionResults} so {@link #saveDrawer()} can resolve the collection behind the
     * label the {@code p:autoComplete} round-trips.
     */
    public List<String> completeCollections(String query) {
        if (draftVocabulary == null) {
            lastCollectionResults = List.of();
            return List.of();
        }
        lastCollectionResults = conceptCollectionService.fetchCollectionsFromRemoteThesaurus(draftVocabulary).stream()
                .filter(c -> query == null || query.isBlank() || c.getLabelToDisplay().toLowerCase().contains(query.toLowerCase()))
                .toList();
        return lastCollectionResults.stream().map(ConceptCollectionDetachedDTO::getLabelToDisplay).toList();
    }

    public void saveDrawer() {
        if (draftOriginalName == null) {
            tableFieldConfigService.createField(project.getId(), selectedTable, selectedTypeName, draftName, draftType, draftDescription);
        } else {
            tableFieldConfigService.updateField(project.getId(), selectedTable, selectedTypeName, draftOriginalName, draftName, draftType, draftDescription);
        }
        saveDraftVocabularyConfig();
        loadConfigs();
        closeDrawer();
        MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "projectTables.drawer.saveSuccess");
    }

    /**
     * Persists the vocabulary source picked in the drawer onto the field just saved by
     * {@link #saveDrawer()}: clears any existing branch/collection restriction for
     * {@code "principal"}, or saves the newly picked branch/collection — a no-op when the source is
     * unset (non-configurable fields) or when the user left the prefilled selection untouched.
     */
    private void saveDraftVocabularyConfig() {
        if ("principal".equals(draftSource)) {
            clearConceptConfigIfAny();
        } else if ("branche".equals(draftSource)) {
            saveBrancheConceptIfChanged();
        } else if ("collection".equals(draftSource)) {
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
        if (draftBrancheConcept == null || draftBrancheConcept.concept().getExternalId().equals(draftOriginalBrancheConceptKey)) {
            return;
        }
        resolveConceptFieldTarget().ifPresentOrElse(
                target -> formConfigService.addConceptConfigFor(target.formConfig(), target.field(), draftBrancheConcept.concept()),
                this::warnFieldNotResolved);
    }

    private void saveCollectionIfChanged() {
        Optional<ConceptCollectionDetachedDTO> selected = lastCollectionResults.stream()
                .filter(c -> c.getLabelToDisplay().equals(draftCollectionName))
                .filter(c -> !c.getExternalId().equals(draftOriginalCollectionKey))
                .findFirst();
        if (selected.isEmpty()) {
            return;
        }
        resolveConceptFieldTarget().ifPresentOrElse(
                target -> formConfigService.addConceptConfigFor(target.formConfig(), target.field(), selected.get()),
                this::warnFieldNotResolved);
    }

    private record ConceptFieldTarget(FormConfig formConfig, CustomFieldConcept field) {
    }

    private Optional<ConceptFieldTarget> resolveConceptFieldTarget() {
        Optional<FormConfig> formConfigLocal = tableFieldConfigService.createOrGetFormConfig(project.getId(), selectedTable, selectedTypeName);
        Optional<CustomField> field = tableFieldConfigService.findField(project.getId(), selectedTable, selectedTypeName, draftName);
        if (formConfigLocal.isEmpty() || field.isEmpty() || !(field.get() instanceof CustomFieldConcept customFieldConcept)) {
            return Optional.empty();
        }
        return Optional.of(new ConceptFieldTarget(formConfigLocal.get(), customFieldConcept));
    }

    private void warnFieldNotResolved() {
        log.warn("Could not resolve the saved field '{}' as a concept field on type '{}' of table {}; " +
                "branch/collection selection was not persisted", draftName, selectedTypeName, selectedTable);
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

    // ===== Identifiants (UE / _default only) =====

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
            boolean numeric = recordingUnitService.findAllNumericalIdentifiersCode().contains(code);
            int digits = parts.length > 1 ? parts[1].length() : IDENT_DEFAULT_DIGITS;
            segments.add(tokenSegment(code, numeric, digits <= 0 ? IDENT_DEFAULT_DIGITS : digits));
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
            if (!ID_UA.equals(seg.getCode())) {
                String specifierChar = seg.isNumeric() ? "0" : "X";
                sb.append(':').append(specifierChar.repeat(Math.max(1, seg.getDigits())));
            }
            sb.append('}');
        }
        return sb.toString();
    }

    private IdentifierSegment textSegment(String text) {
        return IdentifierSegment.builder().token(false).text(text).build();
    }

    private IdentifierSegment tokenSegment(String code, boolean numeric, int digits) {
        RuIdentifierResolver resolver = recordingUnitService.findAllIdentifierResolver().get(code);
        String label = resolver == null ? code : langBean.msg(resolver.getTitleCode());
        return IdentifierSegment.builder().token(true).code(code).label(label).numeric(numeric).digits(digits).build();
    }

    public void addIdentTextSegment() {
        identSegments.add(textSegment(""));
    }

    public void addIdentTokenSegment(String code) {
        boolean numeric = recordingUnitService.findAllNumericalIdentifiersCode().contains(code);
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

    /**
     * The addable token catalog for the format builder — same fixed NUM_UE/NUM_PARENT-first
     * ordering as the moved-from {@code ActionUnitPanel.findAllResolvers()}, minus TYPE_UE/
     * TYPE_PARENT: those are not offered as placeholders here, the user types static text instead.
     */
    public List<RuIdentifierResolver> getIdentifierResolvers() {
        Map<String, RuIdentifierResolver> resolvers = recordingUnitService.findAllIdentifierResolver();
        List<RuIdentifierResolver> result = new ArrayList<>();
        result.add(resolvers.get(NUM_UE));
        result.add(resolvers.get("NUM_PARENT"));
        for (RuIdentifierResolver resolver : resolvers.values()) {
            if (!result.contains(resolver)) {
                result.add(resolver);
            }
        }
        result.removeIf(r -> r == null || "TYPE_UE".equals(r.getCode()) || "TYPE_PARENT".equals(r.getCode()));
        return result;
    }

    /**
     * A preview built from sample values, never the real {@code RuIdentifierResolver.resolve()} —
     * text resolvers persist a dedup row as a side effect of resolving, which would be wrong to
     * trigger just from the user typing in this format builder. The design's own caption already
     * frames this as illustrative ("Exemple généré avec des valeurs types").
     */
    public String getIdentExample() {
        StringBuilder sb = new StringBuilder();
        for (IdentifierSegment seg : identSegments) {
            sb.append(seg.isToken() ? sampleValueFor(seg) : seg.getText());
        }
        return sb.toString();
    }

    private String sampleValueFor(IdentifierSegment seg) {
        return switch (seg.getCode()) {
            case NUM_UE -> zeroPad(142, seg.getDigits());
            case "NUM_PARENT" -> zeroPad(4, seg.getDigits());
            case "NUM_USPATIAL" -> zeroPad(3, seg.getDigits());
            case "TYPE_UE" -> truncate("CERAMIQUE", seg.getDigits());
            case "TYPE_PARENT" -> truncate("SONDAGE", seg.getDigits());
            case ID_UA -> "UA1";
            default -> seg.getLabel();
        };
    }

    private String zeroPad(int value, int digits) {
        String raw = Integer.toString(value);
        int width = Math.max(1, digits);
        return raw.length() >= width ? raw : "0".repeat(width - raw.length()) + raw;
    }

    private String truncate(String value, int digits) {
        int width = digits <= 0 ? IDENT_DEFAULT_DIGITS : digits;
        return value.length() > width ? value.substring(0, width) : value;
    }

    public void saveIdentConfig() {
        if (!isIdentTabAvailable()) return;

        String format = serializeFormat(identSegments);
        if (identifierFormatIsInvalid(format)) return;

        project.setMinRecordingUnitCode(identFirst);
        project.setMaxRecordingUnitCode(identLast);
        project.setRecordingUnitIdentifierFormat(format);
        project.setRecordingUnitIdentifierLang(langBean.getLanguageCode());

        actionUnitService.save(project);
        loadIdentConfig();

        MessageUtils.displayInfoMessage(langBean, "actionUnit.settings.success.identifierConfigSaved");
    }

    private boolean identifierFormatIsInvalid(String format) {
        if (format == null || format.isEmpty()) {
            MessageUtils.displayErrorMessage(langBean, "actionUnit.settings.error.missingNumUe");
            return true;
        }

        boolean containsNumRu = false;
        Matcher matcher = IDENT_PLACEHOLDER_PATTERN.matcher(format);

        String strippedFormat = format.replaceAll(IDENT_PLACEHOLDER_PATTERN.pattern(), "");
        if (strippedFormat.contains("{") || strippedFormat.contains("}")) {
            MessageUtils.displayErrorMessage(langBean, "actionUnit.settings.error.invalidIdentifierFormat");
            return true;
        }

        while (matcher.find()) {
            String[] parts = matcher.group(1).split(":", 2);
            String placeholderName = parts[0];

            if (identFormatContainsInvalidCode(placeholderName)) return true;
            containsNumRu = containsNumRu || placeholderName.equals(NUM_UE);

            if (identFormatOfCodeIsNotValid(parts, placeholderName)) return true;
        }

        if (!containsNumRu) {
            MessageUtils.displayErrorMessage(langBean, "actionUnit.settings.error.missingNumUe");
            return true;
        }
        return false;
    }

    private boolean identFormatContainsInvalidCode(String placeholderName) {
        if (!recordingUnitService.findAllIdentifiersCode().contains(placeholderName)) {
            MessageUtils.displayErrorMessage(langBean, "actionUnit.settings.error.invalidIdentifierFormat");
            return true;
        }
        return false;
    }

    private boolean identFormatOfCodeIsNotValid(String[] parts, String placeholderName) {
        if (parts.length <= 1) return false;
        String formatSpecifier = parts[1];
        return identFormatSpecifierIsNotValid(placeholderName, formatSpecifier) || identNumericalFormatIsNotValid(placeholderName, formatSpecifier);
    }

    /**
     * A numeric code (NUM_UE, NUM_PARENT, NUM_USPATIAL) needs a {@code 0+} specifier; any other
     * code needs {@code X+} (and ID_UA may carry none at all — enforced by the caller, which never
     * passes it a specifier to check here). Numeric and text codes are checked by two separate
     * branches — a specifier valid for one must never fall through into the other's check.
     */
    private boolean identFormatSpecifierIsNotValid(String placeholderName, String formatSpecifier) {
        if (recordingUnitService.findAllNumericalIdentifiersCode().contains(placeholderName)) {
            if (!formatSpecifier.matches("0+")) {
                MessageUtils.displayWarnMessage(langBean, "actionUnit.settings.help.numericalFormat", placeholderName);
                return true;
            }
            return false;
        }
        if (!formatSpecifier.matches("X+") || placeholderName.equals(ID_UA)) {
            MessageUtils.displayWarnMessage(langBean, "actionUnit.settings.help.textualFormat", placeholderName);
            return true;
        }
        return false;
    }

    private boolean identNumericalFormatIsNotValid(String placeholderName, String formatSpecifier) {
        if (recordingUnitService.findAllNumericalIdentifiersCode().contains(placeholderName)) {
            long zeroCount = formatSpecifier.chars().filter(ch -> ch == '0').count();
            if (zeroCount > 0 && identLast != null && String.valueOf(identLast).length() > zeroCount) {
                MessageUtils.displayErrorMessage(langBean, "actionUnit.settings.error.insufficientDigits", placeholderName);
                return true;
            }
        }
        return false;
    }

}
