package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.settings.tableconfig.*;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.ConceptCollectionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.ActionUnitDTO;
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
import java.util.List;
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

    private final transient TableFieldConfigService tableFieldConfigService;
    private final transient FormConfigService formConfigService;
    private final transient ConceptService conceptService;
    private final transient ConceptCollectionService conceptCollectionService;
    private final transient VocabularyService vocabularyService;
    private final transient VocabularyMapper vocabularyMapper;
    private final LangBean langBean;

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

    private String newTypeName;

    public ProjectTableFieldSettingsBean(TableFieldConfigService tableFieldConfigService,
                                         FormConfigService formConfigService,
                                         ConceptService conceptService,
                                         ConceptCollectionService conceptCollectionService,
                                         VocabularyService vocabularyService,
                                         VocabularyMapper vocabularyMapper,
                                         LangBean langBean) {
        this.tableFieldConfigService = tableFieldConfigService;
        this.formConfigService = formConfigService;
        this.conceptService = conceptService;
        this.conceptCollectionService = conceptCollectionService;
        this.vocabularyService = vocabularyService;
        this.vocabularyMapper = vocabularyMapper;
        this.langBean = langBean;
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

    public void deleteAdditionalField(String fieldName) {
        tableFieldConfigService.deleteAdditionalField(project.getId(), selectedTable, selectedTypeName, fieldName);
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
        drawerOpen = true;
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
        draftBrancheConcept = null;
        draftCollectionName = null;
        draftVocabulary = null;
        lastCollectionResults = new ArrayList<>();
    }

    public boolean isDraftConfigurable() {
        return draftType != null && draftType.isConfigurable();
    }

    public void selectDraftSource(String source) {
        resetDraftParams();
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
    }

    /**
     * Persists the branch/collection restriction picked in the drawer, if any, onto the field just
     * saved by {@link #saveDrawer()}. A no-op for the {@code "principal"} source (a system field's
     * thesaurus is configured elsewhere) and while no branch/collection has actually been selected.
     */
    private void saveDraftVocabularyConfig() {
        if (!"branche".equals(draftSource) && !"collection".equals(draftSource)) {
            return;
        }

        Optional<ConceptAutocompleteDetachedDTO> selectedConcept = "branche".equals(draftSource)
                ? Optional.ofNullable(draftBrancheConcept)
                : Optional.empty();
        Optional<ConceptCollectionDetachedDTO> selectedCollection = "collection".equals(draftSource)
                ? lastCollectionResults.stream()
                        .filter(c -> c.getLabelToDisplay().equals(draftCollectionName))
                        .findFirst()
                : Optional.empty();
        if (selectedConcept.isEmpty() && selectedCollection.isEmpty()) {
            return;
        }

        Optional<FormConfig> formConfig = tableFieldConfigService.createOrGetFormConfig(project.getId(), selectedTable, selectedTypeName);
        Optional<CustomField> savedField = tableFieldConfigService.getActiveAdditionalFields(project.getId(), selectedTable, selectedTypeName).stream()
                .filter(field -> draftName.equals(field.getLabel()))
                .findFirst();
        if (formConfig.isEmpty() || savedField.isEmpty() || !(savedField.get() instanceof CustomFieldConcept customFieldConcept)) {
            log.warn("Could not resolve the saved field '{}' as a concept field on type '{}' of table {}; " +
                    "branch/collection selection was not persisted", draftName, selectedTypeName, selectedTable);
            return;
        }

        selectedConcept.ifPresent(concept ->
                formConfigService.addConceptConfigFor(formConfig.get(), customFieldConcept, concept.concept()));
        selectedCollection.ifPresent(collection ->
                formConfigService.addConceptConfigFor(formConfig.get(), customFieldConcept, collection));
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

}
