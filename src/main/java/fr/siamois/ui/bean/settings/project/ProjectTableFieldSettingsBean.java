package fr.siamois.ui.bean.settings.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.form.FieldVocabularyConfigException;
import fr.siamois.domain.models.exceptions.form.NoVocabularySelectedException;
import fr.siamois.domain.models.exceptions.form.VocabularyConfigNotSavedException;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FieldDefault;
import fr.siamois.domain.models.form.config.FieldUnits;
import fr.siamois.domain.models.form.config.FieldWidth;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.settings.tableconfig.*;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.ThesaurusInfo;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.form.layout.FormLayoutService;
import fr.siamois.domain.services.identifier.*;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.*;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.dto.entity.vocabulary.ConceptPrefLabelDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptAutocompleteDetachedDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptCollectionDetachedDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.application.FacesMessage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.event.TabChangeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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

    /** Set by Spring (not part of the constructor); null where the bean is built by hand. */
    private transient ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** Set by Spring, like the publisher; null where the bean is built by hand. */
    private transient UnitDefinitionService unitDefinitionService;

    @Autowired(required = false)
    void setUnitDefinitionService(UnitDefinitionService unitDefinitionService) {
        this.unitDefinitionService = unitDefinitionService;
    }

    /** Set by Spring, like the publisher; null where the bean is built by hand. */
    private transient ProjectTableTypeSourceBean typeSourceBean;

    @Autowired(required = false)
    void setTypeSourceBean(ProjectTableTypeSourceBean typeSourceBean) {
        this.typeSourceBean = typeSourceBean;
    }

    private void openTypeSource(ConfigurableTable table) {
        if (typeSourceBean != null) {
            typeSourceBean.open(project, table);
        }
    }

    public static final int TAB_CHAMPS = 0;
    public static final int TAB_APERCU = 1;
    public static final int TAB_DEFINITION = 2;
    public static final int TAB_IDENTIFIANTS = 3;
    public static final String ID_UA = "ID_UA";
    public static final String NUM_UE = "NUM_UE";
    public static final String BRANCHE = "branche";
    /** The OpenTheso instance offered when no thesaurus is known yet. */
    private static final String DEFAULT_THESAURUS_SERVER = "https://thesaurus.mom.fr";
    private static final String INTERNAL_ERROR = "common.error.internal";
    public static final String COLLECTION = "collection";
    /** The source named by a field code looked up in the picked thesaurus. */
    public static final String FIELD_CODE = "code";

    private final transient TableFieldConfigService tableFieldConfigService;
    private final transient FormConfigService formConfigService;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final transient ConceptService conceptService;
    private final transient ConceptCollectionService conceptCollectionService;
    private final transient VocabularyService vocabularyService;
    private final transient VocabularyMapper vocabularyMapper;
    private final transient LabelService labelService;
    private final LangBean langBean;
    private final transient IdentifierResolverRegistry identifierResolverRegistry;
    private final transient FormLayoutService formLayoutService;

    private ActionUnitDTO project;
    private List<ConfigurableTable> tables = new ArrayList<>();
    private ConfigurableTable selectedTable;
    private List<TypeSummary> typesForSelectedTable = new ArrayList<>();
    private String selectedTypeName;
    private int activeTabIndex = TAB_CHAMPS;

    private TypeFormConfig formConfig;
    private TypeFieldsConfig fieldsConfig;

    /**
     * The form's layout as the screen lists it: group headers and fields in one list, a field
     * belonging to the closest header above it. Held as a mutable list rather than derived on every
     * call because the table it backs is row-draggable: PrimeFaces reorders the very list a
     * draggable table reads while decoding the drag (see {@code DraggableRowsFeature}), so a fresh
     * list per call would drop the new order, and an immutable one would make the decode throw.
     */
    private List<LayoutRow> layoutRows = new ArrayList<>();

    private boolean pickerOpen;
    private String pickerQuery;

    private boolean drawerOpen;
    private String draftOriginalName;
    private String draftName;
    private FieldType draftType;
    /** The type the open field had when the drawer opened: null for a new field. */
    private FieldType draftOriginalType;
    /** MEASUREMENT: the concepts that define the measure, shown instead of the free name and description. */
    private ConceptSummary draftMeasureType;
    private ConceptSummary draftMeasureNature;
    /** MEASUREMENT added by the project: what the user picked (labels round-trip through the autocompletes). */
    private String draftMeasureTypeLabel;
    private String draftMeasureNatureLabel;
    private transient List<ConceptAutocompleteDTO> lastMeasureTypeResults = new ArrayList<>();
    private transient List<ConceptAutocompleteDTO> lastMeasureNatureResults = new ArrayList<>();
    private String draftDescription;
    /** BOOLEAN: the answer may stay empty (a third state beside yes and no). */
    private boolean draftAllowNoAnswer;
    /** MEASUREMENT: what the units measure, the units offered, and the one a new answer starts in. */
    private UnitDefinition.Dimension draftDimension;
    private List<String> draftUnitIds = new ArrayList<>();
    private String draftDefaultUnitId;

    private boolean draftIsSystem;
    private String draftFieldCode;
    private String draftSource;
    private String draftThesaurusUrl;
    private boolean draftConnectionTested;
    private transient ConceptAutocompleteDetachedDTO draftBrancheConcept;
    private String draftCollectionName;
    private transient VocabularyDTO draftVocabulary;

    /** The OpenTheso instance picked, the thesauri it publishes and the one picked among them. */
    private String draftServerUrl;
    private transient ThesaurusInfo draftThesaurus;

    /** The field codes the picked thesaurus defines, and the one chosen among them. */
    private transient List<String> draftCodeOptions = new ArrayList<>();
    private String draftCodeChoice;
    private transient String draftOriginalCodeKey;
    private String newServerUrl;
    private transient List<String> draftServerOptions;
    private transient List<ThesaurusInfo> draftThesaurusOptions = new ArrayList<>();

    /**
     * Progress of the branch/collection import {@link #saveDrawer()} triggers when saving a branch or
     * collection source — a large collection resolves its concepts one HTTP call at a time (see
     * {@code ConceptApiUtils#saveAllConceptsOfBranch}) and can take long enough to warrant a progress
     * bar, following the same session-scoped-bean + PrimeFaces-self-polling pattern as
     * {@code InstitutionDialogBean}.
     */
    private final ProgressWrapper progressWrapper = new ProgressWrapper();

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
    private Integer identDigitsErrorIndex;

    private static final Pattern IDENT_PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private static final int IDENT_DEFAULT_DIGITS = 3;

    public ProjectTableFieldSettingsBean(TableFieldConfigService tableFieldConfigService,
                                         FormConfigService formConfigService,
                                         ConceptService conceptService,
                                         ConceptCollectionService conceptCollectionService,
                                         VocabularyService vocabularyService,
                                         VocabularyMapper vocabularyMapper,
                                         LabelService labelService,
                                         LangBean langBean, IdentifierResolverRegistry identifierResolverRegistry,
                                         FormLayoutService formLayoutService,
                                         FieldConfigurationService fieldConfigurationService) {
        this.tableFieldConfigService = tableFieldConfigService;
        this.formConfigService = formConfigService;
        this.fieldConfigurationService = fieldConfigurationService;
        this.conceptService = conceptService;
        this.conceptCollectionService = conceptCollectionService;
        this.vocabularyService = vocabularyService;
        this.vocabularyMapper = vocabularyMapper;
        this.labelService = labelService;
        this.langBean = langBean;
        this.identifierResolverRegistry = identifierResolverRegistry;
        this.formLayoutService = formLayoutService;
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
        layoutRows = new ArrayList<>();
        pickerOpen = false;
        pickerQuery = null;
        closeDrawer();
        expandedNodes.clear();
        treeCache.clear();
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
        expandedNodes.add(tableNode(table));
        typesForSelectedTable = tableFieldConfigService.listTypes(project.getId(), table);
        openTypeSource(table);
        // a table opens on its first type; one with none yet (its thesaurus gave it nothing) opens empty
        selectType(typesForSelectedTable.isEmpty() ? null : typesForSelectedTable.get(0).getName());
    }

    /**
     * The table's own page, from its name in the tree: what the table is for and a way to add a type,
     * with no type open.
     */
    public void selectTableOnly(ConfigurableTable table) {
        selectedTable = table;
        typesForSelectedTable = tableFieldConfigService.listTypes(project.getId(), table);
        openTypeSource(table);
        selectType(null);
    }

    /** The table's types are read from another source: lists them again. */
    public void reloadTypesOfSelectedTable() {
        selectTableOnly(selectedTable);
    }

    /**
     * Opens the page on a type — given by its concept — of a table, and on one of its fields when asked: where
     * a fiche's gear and its fields' "configure" lead. The tree is unfolded down to what is opened; a type or
     * a field that is not found leaves the page on the table.
     */
    public void openOn(ConfigurableTable table, Long typeConceptId, Long fieldId) {
        openOn(table, typeConceptId, fieldId, null);
    }

    /** As above, the field being designated by the field code it reads its values from when it has no id at hand. */
    public void openOn(ConfigurableTable table, Long typeConceptId, Long fieldId, String fieldCode) {
        selectTableOnly(table);
        Optional<Concept> concept = conceptService.findById(typeConceptId);
        if (concept.isEmpty()) {
            return;
        }
        String label = labelService.findLabelOf(concept.get(), langBean.getLanguageCode()).getLabel();
        Optional<String> typeName = typesForSelectedTable.stream()
                .map(TypeSummary::getName)
                .filter(name -> name.equalsIgnoreCase(label))
                .findFirst();
        if (typeName.isEmpty()) {
            return;
        }
        selectType(typeName.get());
        expandedNodes.add(tableNode(table));
        expandedNodes.add(typeNode(table, typeName.get()));
        if ((fieldId != null || fieldCode != null) && fieldsConfig != null) {
            fieldsConfig.getFields().stream()
                    .filter(candidate -> fieldId != null ? fieldId.equals(candidate.getId()) : fieldCode.equals(candidate.getSourceLabel()))
                    .findFirst()
                    .ifPresent(this::openDrawerForEdit);
        }
    }

    /** What the selected table is for, shown on its page. */
    public String getSelectedTableDescription() {
        return selectedTable == null ? "" : langBean.msg("projectTables.table.description." + selectedTable.name());
    }

    public void selectType(String typeName) {
        // a field open in the main panel belongs to the type being left
        closeDrawer();
        if (!java.util.Objects.equals(selectedTypeName, typeName)) {
            collapsedGroups.clear();
            fieldSearch = null;
        }
        selectedTypeName = typeName;
        // a type belongs to a table: without one there is nothing to load
        if (typeName == null || selectedTable == null) {
            formConfig = null;
            setFieldsConfig(null);
            layoutRows = new ArrayList<>();
            return;
        }
        loadConfigs();
    }

    private void loadConfigs() {
        treeCache.clear();
        formConfig = tableFieldConfigService.getFormConfig(project.getId(), selectedTable, selectedTypeName);
        setFieldsConfig(tableFieldConfigService.getFieldsConfig(project.getId(), selectedTable, selectedTypeName));
        reloadLayoutRows();
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
        identDigitsErrorIndex = null;
    }

    public void setFieldsConfig(TypeFieldsConfig fieldsConfig) {
        this.fieldsConfig = fieldsConfig;
    }

    public void reloadLayoutRows() {
        layoutRows = new ArrayList<>(formLayoutService.rowsOf(project.getId(), selectedTable, selectedTypeName));
        // a field is either part of the type's form or it is not: a removed one (stored as inactive) is not listed.
        // Saving the arrangement without it is harmless — the service files anything unplaced in the last group.
        layoutRows.removeIf(row -> row.isFieldRow() && !row.getField().isActive());
        refreshHiddenRows();
        layoutRows.stream().filter(LayoutRow::isGroup)
                .forEach(row -> row.setDisplayLabel(resolveGroupLabel(row.getGroupLabel())));
    }

    // ---- the layout table: groups fold, and a search narrows the rows to the fields whose name matches ----

    /** The groups the user folded, by stored id (or label for one not saved yet) — their fields are not shown. */
    private final Set<String> collapsedGroups = new HashSet<>();
    /** What the search box holds. */
    private String fieldSearch;
    /** Keys of the rows left out of the table (folded group, no match): they stay in the list so a drag keeps its indexes. */
    private final Set<String> hiddenRowKeys = new HashSet<>();

    /** The group a field being added is meant for (its "+ champ" button), or null for the end of the form. */
    private String targetGroupKey;
    /** The row just added: the page scrolls to it and highlights it. */
    private String focusKey;

    public boolean isFocusRow(LayoutRow row) {
        if (focusKey == null) return false;
        return focusKey.equals(row.isGroup() ? groupKey(row) : "f:" + row.getField().getName());
    }

    /** The "+ champ" button of a group: the field the picker adds lands in that group. */
    public void addFieldToGroup(LayoutRow group) {
        targetGroupKey = groupKey(group);
        openPicker();
    }

    /**
     * Once a field is added to the type: moves it to the end of the group it was asked for (if any), puts the
     * search and the folded groups aside so the new row can be seen, and marks it so the page scrolls to it.
     */
    private void placeAfterAdd(String fieldName) {
        fieldSearch = null;
        collapsedGroups.clear();
        LayoutRow row = layoutRows.stream()
                .filter(r -> r.isFieldRow() && r.getField().getName().equals(fieldName))
                .findFirst().orElse(null);
        if (row != null && targetGroupKey != null) {
            layoutRows.remove(row);
            layoutRows.add(endOfGroup(targetGroupKey), row);
            saveRows();
        }
        targetGroupKey = null;
        focusKey = "f:" + fieldName;
        refreshHiddenRows();
    }

    /** The index just after the last row of the group (the end of the list when the group is gone). */
    private int endOfGroup(String key) {
        int at = layoutRows.size();
        for (int i = 0; i < layoutRows.size(); i++) {
            if (layoutRows.get(i).isGroup() && groupKey(layoutRows.get(i)).equals(key)) {
                at = i + 1;
                while (at < layoutRows.size() && !layoutRows.get(at).isGroup()) at++;
                break;
            }
        }
        return at;
    }

    private static String groupKey(LayoutRow group) {
        return group.getGroupId() != null ? "id:" + group.getGroupId() : "label:" + group.getGroupLabel();
    }

    public boolean isGroupCollapsed(LayoutRow group) {
        return collapsedGroups.contains(groupKey(group));
    }

    public void toggleGroup(LayoutRow group) {
        if (!collapsedGroups.remove(groupKey(group))) {
            collapsedGroups.add(groupKey(group));
        }
        refreshHiddenRows();
    }

    public boolean isRowHidden(LayoutRow row) {
        return hiddenRowKeys.contains(row.getKey());
    }

    public void onFieldSearch() {
        refreshHiddenRows();
    }

    /**
     * Works out which rows the table leaves out. While searching, a field shows only if its name matches and
     * a group only if one of its fields does (folding is set aside, so a match is never hidden); otherwise the
     * fields of a folded group are hidden.
     */
    private void refreshHiddenRows() {
        hiddenRowKeys.clear();
        String needle = fieldSearch == null ? "" : fieldSearch.toLowerCase().trim();
        boolean searching = !needle.isEmpty();
        LayoutRow group = null;
        List<LayoutRow> members = new ArrayList<>();
        for (LayoutRow row : layoutRows) {
            if (row.isGroup()) {
                settleGroup(group, members, searching, needle);
                group = row;
                members = new ArrayList<>();
            } else {
                members.add(row);
            }
        }
        settleGroup(group, members, searching, needle);
    }

    private void settleGroup(LayoutRow group, List<LayoutRow> members, boolean searching, String needle) {
        if (group == null) {
            return;
        }
        if (searching) {
            boolean any = false;
            for (LayoutRow member : members) {
                if (resolveFieldLabel(member.getField()).toLowerCase().contains(needle)) {
                    any = true;
                } else {
                    hiddenRowKeys.add(member.getKey());
                }
            }
            if (!any) {
                hiddenRowKeys.add(group.getKey());
            }
        } else if (isGroupCollapsed(group)) {
            members.forEach(member -> hiddenRowKeys.add(member.getKey()));
        }
    }

    public long getFieldCount() {
        return layoutRows.stream().filter(LayoutRow::isFieldRow).count();
    }

    /**
     * The table's own "type" field — the pivot whose value picks which {@link FormConfig} applies —
     * is not an optional field of the form, it is what selects the form: it stays in its place but
     * cannot be switched off or made optional.
     */
    public boolean isPivotField(TypeFieldFormConfig field) {
        return selectedTable != null && selectedTable.getFieldCode().equals(field.getSourceLabel());
    }

    /**
     * A group's label is either the message key of one of the original sections or free text.
     */
    public String resolveGroupLabel(String label) {
        if (label == null) return "";
        try {
            return langBean.msg(label);
        } catch (RuntimeException e) {
            return label;
        }
    }

    public List<FieldWidth> getFieldWidths() {
        return List.of(FieldWidth.values());
    }

    public String widthLabel(FieldWidth width) {
        return langBean.msg("projectTables.layout.width." + width.name());
    }

    /**
     * Persists what the user dropped: PrimeFaces has already applied the move to {@link #layoutRows}
     * by the time this runs, so the listener only reads the resulting order off the list. A field
     * dropped above the first header belongs to no group: the move is refused.
     */
    public void onLayoutReorder() {
        saveRows();
    }

    private void saveRows() {
        if (layoutRows.isEmpty()) return;
        if (!layoutRows.get(0).isGroup()) {
            MessageUtils.displayWarnMessage(langBean, "projectTables.layout.fieldBeforeGroup");
            reloadLayoutRows();
            return;
        }
        List<FormLayoutService.GroupSpec> specs = new ArrayList<>();
        Long id = null;
        String label = null;
        List<String> names = null;
        for (LayoutRow row : layoutRows) {
            if (row.isGroup()) {
                if (names != null) specs.add(new FormLayoutService.GroupSpec(id, label, names));
                id = row.getGroupId();
                label = storedLabelOf(row);
                names = new ArrayList<>();
            } else {
                names.add(row.getField().getName());
            }
        }
        specs.add(new FormLayoutService.GroupSpec(id, label, names));
        formLayoutService.saveArrangement(project.getId(), selectedTable, selectedTypeName, specs);
        reloadLayoutRows();
    }

    /** The label to store: the stored key stays as it is while the user left its translation untouched. */
    private String storedLabelOf(LayoutRow header) {
        String shown = header.getDisplayLabel();
        if (shown == null || shown.isBlank() || shown.equals(resolveGroupLabel(header.getGroupLabel()))) {
            return header.getGroupLabel();
        }
        return shown.trim();
    }

    public void onGroupRenamed() {
        saveRows();
    }

    public void onWidthChanged(LayoutRow row) {
        formLayoutService.setFieldWidth(project.getId(), selectedTable, selectedTypeName, row.getField().getName(), row.getWidth());
        reloadLayoutRows();
    }

    public void addGroup() {
        layoutRows.add(LayoutRow.header(null, langBean.msg("projectTables.layout.newGroup")));
        saveRows();
        fieldSearch = null;
        collapsedGroups.clear();
        // the new group is the last one, and has its stored id by now
        layoutRows.stream().filter(LayoutRow::isGroup).reduce((first, second) -> second)
                .ifPresent(last -> focusKey = groupKey(last));
        refreshHiddenRows();
    }

    /** A group can be deleted once it holds no field: its header is then followed by another or by nothing. */
    public void deleteGroup(LayoutRow header) {
        int index = layoutRows.indexOf(header);
        boolean empty = index >= 0 && (index == layoutRows.size() - 1 || layoutRows.get(index + 1).isGroup());
        if (!empty) {
            MessageUtils.displayWarnMessage(langBean, "projectTables.layout.groupNotEmpty");
            return;
        }
        if (layoutRows.stream().filter(LayoutRow::isGroup).count() == 1) {
            MessageUtils.displayWarnMessage(langBean, "projectTables.layout.lastGroup");
            return;
        }
        layoutRows.remove(index);
        saveRows();
    }

    /** Moves a group, with its fields, one group up (-1) or down (+1). */
    public void moveGroup(LayoutRow header, int direction) {
        int start = layoutRows.indexOf(header);
        if (start < 0) return;
        int end = start + 1;
        while (end < layoutRows.size() && !layoutRows.get(end).isGroup()) end++;
        List<LayoutRow> block = new ArrayList<>(layoutRows.subList(start, end));
        int target;
        if (direction < 0) {
            target = start - 1;
            while (target >= 0 && !layoutRows.get(target).isGroup()) target--;
            if (target < 0) return;
        } else {
            if (end >= layoutRows.size()) return;
            int nextEnd = end + 1;
            while (nextEnd < layoutRows.size() && !layoutRows.get(nextEnd).isGroup()) nextEnd++;
            target = start + (nextEnd - end);
        }
        layoutRows.subList(start, end).clear();
        layoutRows.addAll(target, block);
        saveRows();
    }

    // ---- the tree: tables and types fold and unfold on their own, independently of what is selected ----

    /** Open nodes of the tree: "T:&lt;table&gt;" for a table, "Y:&lt;table&gt;:&lt;type&gt;" for a type. */
    private final Set<String> expandedNodes = new HashSet<>();
    /** What the open nodes loaded (types of a table, fields of a type) — dropped whenever a config is reloaded. */
    private final Map<String, Object> treeCache = new HashMap<>();

    private static String tableNode(ConfigurableTable table) {
        return "T:" + table.name();
    }

    private static String typeNode(ConfigurableTable table, String typeName) {
        return "Y:" + table.name() + ":" + typeName;
    }

    public boolean isTableExpanded(ConfigurableTable table) {
        return expandedNodes.contains(tableNode(table));
    }

    public boolean isTypeExpanded(ConfigurableTable table, String typeName) {
        return expandedNodes.contains(typeNode(table, typeName));
    }

    public void toggleTable(ConfigurableTable table) {
        toggleNode(tableNode(table));
    }

    public void toggleType(ConfigurableTable table, String typeName) {
        toggleNode(typeNode(table, typeName));
    }

    private void toggleNode(String node) {
        if (!expandedNodes.remove(node)) {
            expandedNodes.add(node);
        }
    }

    /** The types of a table, whether it is the selected one or only unfolded in the tree. */
    @SuppressWarnings("unchecked")
    public List<TypeSummary> typesOf(ConfigurableTable table) {
        if (table == selectedTable && typesForSelectedTable != null) {
            return typesForSelectedTable;
        }
        return (List<TypeSummary>) treeCache.computeIfAbsent("types:" + table.name(),
                key -> tableFieldConfigService.listTypes(project.getId(), table));
    }

    /** The fields of a type, in the form's order for the selected type and as configured for the others. */
    @SuppressWarnings("unchecked")
    public List<TypeFieldFormConfig> fieldsOf(ConfigurableTable table, String typeName) {
        if (table == selectedTable && typeName.equals(selectedTypeName)) {
            return layoutRows.stream().filter(LayoutRow::isFieldRow).map(LayoutRow::getField).toList();
        }
        return (List<TypeFieldFormConfig>) treeCache.computeIfAbsent("fields:" + table.name() + ":" + typeName,
                key -> tableFieldConfigService.getFieldsConfig(project.getId(), table, typeName).getFields().stream()
                        .filter(TypeFieldFormConfig::isActive)
                        .toList());
    }

    /** Selects a type of any table of the tree (and the table with it). */
    public void selectTypeOf(ConfigurableTable table, String typeName) {
        if (table != selectedTable) {
            selectedTable = table;
            typesForSelectedTable = tableFieldConfigService.listTypes(project.getId(), table);
        }
        selectType(typeName);
    }

    /** Opens a field of any type of the tree in the main panel, selecting its type first when needed. */
    public void openFieldOf(ConfigurableTable table, String typeName, TypeFieldFormConfig field) {
        if (table != selectedTable || !typeName.equals(selectedTypeName)) {
            selectTypeOf(table, typeName);
        }
        if (fieldsConfig == null) {
            return;
        }
        fieldsConfig.getFields().stream()
                .filter(candidate -> candidate.getName().equals(field.getName()))
                .findFirst()
                .ifPresent(this::openDrawerForEdit);
    }

    /** The "+ champ" of a type in the tree: selects that type, then opens the picker that adds a field to it. */
    public void addFieldToType(ConfigurableTable table, String typeName) {
        if (table != selectedTable || !typeName.equals(selectedTypeName)) {
            selectTypeOf(table, typeName);
        }
        addField();
    }

    public int getTypeCountFor(ConfigurableTable table) {
        return tableFieldConfigService.listTypes(project.getId(), table).size();
    }

    public void onTabChange(TabChangeEvent<?> event) {
        // activeTabIndex is bound directly via p:tabView activeIndex, nothing else to do here
    }

    /** Whether the field open in the main panel can be taken out of the type's form (not the table's type field, nor a locked one). */
    public boolean isDraftRemovable() {
        if (draftOriginalName == null || fieldsConfig == null) return false;
        return fieldsConfig.getFields().stream()
                .filter(f -> f.getName().equals(draftOriginalName))
                .anyMatch(f -> !isPivotField(f) && !f.isInstitutionLocked());
    }

    /** "Retirer du formulaire", from the field's own view: removes it, then goes back to the type's view. */
    public void removeDraftField() {
        if (draftOriginalName == null || fieldsConfig == null) return;
        fieldsConfig.getFields().stream()
                .filter(f -> f.getName().equals(draftOriginalName))
                .findFirst()
                .ifPresent(this::removeField);
        closeDrawer();
    }

    /**
     * Takes a field out of the type's form, a system field included: it stops being listed and no longer
     * appears in the form or the table. An additional field is deleted (unless it already has answers);
     * a system field is only set aside, and can be picked again from the catalog.
     */
    public void removeField(TypeFieldFormConfig field) {
        if (isPivotField(field) || field.isInstitutionLocked()) {
            return;
        }
        if (field.isSystemField()) {
            formLayoutService.ensureOwnLayout(project.getId(), selectedTable, selectedTypeName);
            tableFieldConfigService.setFieldActive(project.getId(), selectedTable, selectedTypeName, field.getName(), false);
            loadConfigs();
            return;
        }
        deleteAdditionalField(field.getName());
    }

    public void toggleFieldMandatory(TypeFieldFormConfig field) {
        formLayoutService.ensureOwnLayout(project.getId(), selectedTable, selectedTypeName);
        tableFieldConfigService.setFieldMandatory(project.getId(), selectedTable, selectedTypeName, field.getName(), field.isMandatory());
        reloadLayoutRows();
    }

    /**
     * Deletes an additional field, unless the project already holds answers for it — the service
     * then keeps the field and the user is told why the row is still there.
     */
    public void deleteAdditionalField(String fieldName) {
        formLayoutService.ensureOwnLayout(project.getId(), selectedTable, selectedTypeName);
        boolean deleted = tableFieldConfigService.deleteAdditionalField(project.getId(), selectedTable, selectedTypeName, fieldName);
        if (!deleted) {
            MessageUtils.displayWarnMessage(langBean, "projectTables.champs.deleteRefusedInUse", fieldName);
            return;
        }
        loadConfigs();
    }

    public void addField() {
        targetGroupKey = null;
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
        List<FieldCatalogEntry> entries = new ArrayList<>(removedSystemFieldEntries());
        entries.addAll(tableFieldConfigService.searchFieldCatalog(project.getId(), selectedTable, selectedTypeName, pickerQuery));
        return entries;
    }

    /** The system fields taken out of this type, offered again by the picker. */
    private List<FieldCatalogEntry> removedSystemFieldEntries() {
        if (fieldsConfig == null) return List.of();
        String needle = pickerQuery == null ? "" : pickerQuery.toLowerCase().trim();
        return fieldsConfig.getFields().stream()
                .filter(f -> f.isSystemField() && !f.isActive())
                .filter(f -> needle.isBlank() || resolveFieldLabel(f).toLowerCase().contains(needle))
                .map(f -> FieldCatalogEntry.builder()
                        .name(resolveFieldLabel(f))
                        .type(f.getType())
                        .description(f.getDescription())
                        .build())
                .toList();
    }

    public void pickExistingField(FieldCatalogEntry entry) {
        formLayoutService.ensureOwnLayout(project.getId(), selectedTable, selectedTypeName);
        TypeFieldFormConfig removed = fieldsConfig == null ? null : fieldsConfig.getFields().stream()
                .filter(f -> f.isSystemField() && !f.isActive() && resolveFieldLabel(f).equals(entry.getName()))
                .findFirst().orElse(null);
        String addedName = removed != null ? removed.getName() : entry.getName();
        if (removed != null) {
            // a system field put back: it is switched on again rather than added
            tableFieldConfigService.setFieldActive(project.getId(), selectedTable, selectedTypeName, removed.getName(), true);
        } else {
            tableFieldConfigService.addExistingField(project.getId(), selectedTable, selectedTypeName, entry.getName());
        }
        pickerOpen = false;
        loadConfigs();
        placeAfterAdd(addedName);
    }

    public void openNewFieldDrawer() {
        pickerOpen = false;
        openDrawerForCreate();
    }

    public void openDrawerForCreate() {
        draftOriginalName = null;
        draftName = "";
        draftType = FieldType.TEXT;
        draftOriginalType = null;
        draftMeasureType = null;
        draftMeasureNature = null;
        draftMeasureTypeLabel = null;
        draftMeasureNatureLabel = null;
        draftDescription = "";
        draftIsSystem = false;
        loadDraftOptions(FieldOptions.NONE);
        resetDraftParams();
        drawerOpen = true;
    }

    public void openDrawerForEdit(TypeFieldFormConfig field) {
        draftOriginalName = field.getName();
        draftName = field.getName();
        draftType = field.getType();
        draftOriginalType = field.getType();
        draftDescription = field.getDescription();
        draftIsSystem = field.isSystemField();
        loadDraftOptions(field.getOptions() == null ? FieldOptions.NONE : field.getOptions());
        draftMeasureType = field.getMeasureType();
        draftMeasureNature = field.getMeasureNature();
        draftMeasureTypeLabel = draftMeasureType == null ? null : draftMeasureType.name();
        draftMeasureNatureLabel = draftMeasureNature == null ? null : draftMeasureNature.name();
        draftFieldCode = field.getSourceLabel();
        resetDraftParams();
        draftSource = null;
        if (draftIsSystem && field.getType().isConfigurable()) {
            startFromDefaultFieldCode();
        }
        if (isDraftConfigurable()) {
            prefillVocabularyConfig();
            browseValues("", 0);
        }
        loadDraftDefault();
        drawerOpen = true;
        announceOpenField(field);
    }

    /** Tells the rules editor which field is open, so its section shows that field's conditional rules. */
    private void announceOpenField(TypeFieldFormConfig field) {
        if (eventPublisher != null && field != null) {
            eventPublisher.publishEvent(new FieldOpenedEvent(field));
        }
    }

    /**
     * The field's Save: its definition and vocabulary are written unless it is a system field with nothing to
     * edit there (name, type and description are locked). The conditional rules are saved separately, after this.
     */
    public void saveDrawerIfEditable() {
        if (!draftIsSystem || isDraftConfigurable() || isDraftMeasurement()) {
            saveDrawer();
        }
    }

    /**
     * Loads the field's current branch/collection restriction, if any, so the drawer shows what is
     * actually configured instead of always starting blank. Leaves the default source
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
        } else if (config.isCollectionConfig()) {
            prefillCollection(config.getCollection());
        } else if (config.isFieldCodeConfig()) {
            prefillFieldCode(config);
        }
    }

    private void prefillFieldCode(ConceptFieldFormConfig config) {
        draftSource = FIELD_CODE;
        draftVocabulary = vocabularyMapper.convert(config.getVocabulary());
        draftThesaurusUrl = draftVocabulary.completeUri();
        draftConnectionTested = true;
        selectServerOf(draftVocabulary);
        loadDraftCodeOptions();
        draftCodeChoice = config.getFieldCode();
        draftOriginalCodeKey = codeKey(draftVocabulary, config.getFieldCode());
    }

    private static String codeKey(VocabularyDTO vocabulary, String code) {
        return vocabulary.getId() + "|" + code;
    }

    private void prefillBranch(Concept branchTopTerm) {
        draftSource = BRANCHE;
        draftVocabulary = vocabularyMapper.convert(branchTopTerm.getVocabulary());
        draftThesaurusUrl = draftVocabulary.completeUri();
        draftConnectionTested = true;
        selectServerOf(draftVocabulary);

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
        selectServerOf(draftVocabulary);

        ConceptCollectionDetachedDTO match = conceptCollectionService.fetchCollectionsFromRemoteThesaurus(draftVocabulary).stream()
                .filter(c -> c.getExternalId().equals(collection.getExternalId()))
                .findFirst()
                .orElseGet(() -> new ConceptCollectionDetachedDTO(collection.getExternalId(), draftVocabulary, collection.getExternalId(), List.of()));
        lastCollectionResults = List.of(match);
        draftCollectionName = match.getLabelToDisplay();
        draftOriginalCollectionKey = match.getExternalId();
    }

    /** What the banner names while a field is open: its caption, or "new field" while one is being created. */
    public String getDraftDisplayName() {
        if (isDraftMeasureSelectable() && draftMeasureTypeLabel != null && !draftMeasureTypeLabel.isBlank()) {
            return measureName();
        }
        if (isDraftDefinedByMeasureConcepts()) {
            String nature = getDraftMeasureNatureName();
            return nature.isBlank() ? getDraftMeasureTypeName() : getDraftMeasureTypeName() + " " + nature;
        }
        return draftCaption();
    }

    private String draftCaption() {
        if (draftName == null || draftName.isBlank()) {
            return langBean.msg("projectTables.drawer.newFieldName");
        }
        return draftIsSystem ? langBean.msg(draftName) : draftName;
    }

    public void closeDrawer() {
        drawerOpen = false;
        draftOriginalName = null;
        draftName = null;
        draftType = null;
        draftOriginalType = null;
        draftMeasureType = null;
        draftMeasureNature = null;
        draftMeasureTypeLabel = null;
        draftMeasureNatureLabel = null;
        draftDescription = null;
        loadDraftOptions(FieldOptions.NONE);
        draftIsSystem = false;
        draftFieldCode = null;
        resetDraftParams();
    }

    private void resetDraftParams() {
        draftSource = null;
        draftThesaurusUrl = null;
        draftConnectionTested = false;
        draftVocabulary = null;
        draftServerUrl = null;
        draftThesaurus = null;
        clearValuesPage();
        newServerUrl = null;
        draftServerOptions = null;
        draftThesaurusOptions = new ArrayList<>();
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
        draftCodeChoice = null;
        draftCodeOptions = new ArrayList<>();
        draftOriginalCodeKey = null;
    }

    public boolean isDraftConfigurable() {
        return draftType != null && draftType.isConfigurable();
    }

    public void selectDraftSource(String source) {
        clearSourceSelection();
        draftSource = source;
        if (FIELD_CODE.equals(source) && startFromDefaultFieldCode()) {
            return;
        }
        offerDocumentationThesaurus();
        draftSource = source;
        if (FIELD_CODE.equals(source)) {
            loadDraftCodeOptions();
        }
    }

    /**
     * The source a field with a code of its own reads by default: that code, in the thesaurus the code is read in by
     * default. Offered as the starting point of the "field code" source, where the thesaurus and the code can both be changed.
     *
     * @return whether the field has such a default, and the pickers now show it
     */
    private boolean startFromDefaultFieldCode() {
        Optional<Vocabulary> defaultVocabulary = defaultVocabularyOfFieldCode();
        if (defaultVocabulary.isEmpty()) {
            return false;
        }
        draftSource = FIELD_CODE;
        draftVocabulary = vocabularyMapper.convert(defaultVocabulary.get());
        draftThesaurusUrl = draftVocabulary.completeUri();
        draftConnectionTested = true;
        selectServerOf(draftVocabulary);
        loadDraftCodeOptions();
        draftCodeChoice = draftFieldCode;
        draftOriginalCodeKey = codeKey(draftVocabulary, draftFieldCode);
        return true;
    }

    private Optional<Vocabulary> defaultVocabularyOfFieldCode() {
        if (draftFieldCode == null || draftFieldCode.isBlank() || project == null || project.getCreatedByInstitution() == null) {
            return Optional.empty();
        }
        return fieldConfigurationService.findDefaultVocabularyOfFieldCode(
                project.getCreatedByInstitution().getId(), project.getId(), draftFieldCode);
    }

    /** Whether the picked thesaurus and code are the field's default: nothing then needs to be stored for the field. */
    private boolean isDefaultFieldCodeChoice() {
        return draftCodeChoice != null && draftCodeChoice.equals(draftFieldCode)
                && defaultVocabularyOfFieldCode()
                .map(found -> found.getId() != null && found.getId().equals(draftVocabulary.getId()))
                .orElse(false);
    }

    /**
     * When no thesaurus has been picked yet, starts from the one the project documents its fields in, then
     * the institution's: that is where its own fields and types live, so it is the likeliest source.
     */
    private void offerDocumentationThesaurus() {
        if (draftThesaurusUrl != null && !draftThesaurusUrl.isBlank()) {
            return;
        }
        Optional<String> url = fieldConfigurationService.findVocabularyUrlOfActionUnitId(project.getId());
        if (url.isEmpty() && project.getCreatedByInstitution() != null) {
            url = fieldConfigurationService.findVocabularyUrlOfInstitutionId(project.getCreatedByInstitution().getId());
        }
        if (url.isEmpty()) {
            return;
        }
        draftThesaurusUrl = url.get();
        testThesaurusConnection();
        if (draftConnectionTested) {
            selectServerOf(draftVocabulary);
        }
    }

    /** The field codes of the picked thesaurus, which is read the first time it is offered. */
    private void loadDraftCodeOptions() {
        draftCodeOptions = new ArrayList<>();
        if (draftVocabulary == null || !draftConnectionTested) {
            return;
        }
        try {
            Vocabulary vocabulary = vocabularyService.findVocabularyById(draftVocabulary.getId());
            draftCodeOptions = new ArrayList<>(fieldConfigurationService.findFieldCodesOf(vocabulary));
            if (draftCodeOptions.isEmpty()) {
                showDrawerError("projectTables.drawer.params.codeNone");
            }
        } catch (fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException e) {
            showDrawerError("myProfile.thesaurus.siamois.invalid");
        } catch (fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException | RuntimeException e) {
            log.error("Could not read the field codes of thesaurus {}", draftVocabulary.getExternalVocabularyId(), e);
            showDrawerError("thesaurus.error.processingExpansion");
        }
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
            if (FIELD_CODE.equals(draftSource)) {
                loadDraftCodeOptions();
            }
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

    /** Page size of the list of values the saved configuration offers. */
    public static final int VALUES_PAGE_SIZE = 20;

    private transient List<ConceptAutocompleteDTO> valuesItems = new ArrayList<>();
    private String valuesQuery;
    private int valuesOffset;
    private boolean valuesHasNext;
    private int valuesTotal;
    private boolean valuesTotalCapped;
    private boolean valuesAvailable;

    private void clearValuesPage() {
        valuesItems = new ArrayList<>();
        valuesQuery = null;
        valuesOffset = 0;
        valuesHasNext = false;
        valuesTotal = 0;
        valuesTotalCapped = false;
        valuesAvailable = false;
    }

    /**
     * Reads a page of the values the field's saved configuration offers. Nothing is listed until a
     * branch or collection has been saved, as the list comes from the database.
     */
    private void browseValues(String query, int offset) {
        clearValuesPage();
        Optional<FormConfig> typeFormConfig = tableFieldConfigService.findFormConfig(project.getId(), selectedTable, selectedTypeName);
        Optional<CustomField> field = tableFieldConfigService.findField(project.getId(), selectedTable, selectedTypeName, draftOriginalName);
        if (typeFormConfig.isEmpty() || field.isEmpty() || !(field.get() instanceof CustomFieldConcept conceptField)) {
            return;
        }
        Optional<ConceptFieldFormConfig> config = formConfigService.findConceptConfigFor(typeFormConfig.get(), conceptField)
                .filter(stored -> !stored.isNotValid())
                .or(() -> defaultFieldCodeConfig(typeFormConfig.get()));
        if (config.isEmpty()) {
            return;
        }
        try {
            FieldConfigurationService.ValuesPage page = fieldConfigurationService.fetchValuesPage(
                    conceptField, config.get(), langBean.getLanguageCode(), query, offset, VALUES_PAGE_SIZE);
            valuesItems = new ArrayList<>(page.items());
            valuesHasNext = page.hasNext();
            valuesTotal = page.total();
            valuesTotalCapped = page.totalCapped();
            valuesQuery = query;
            valuesOffset = offset;
            valuesAvailable = true;
        } catch (RuntimeException e) {
            log.error("Could not list the values of field {}", draftOriginalName, e);
        }
    }

    /**
     * What a field left on its default code reads: nothing is stored for it, so its list is that code in the
     * thesaurus the code is read in by default.
     */
    private Optional<ConceptFieldFormConfig> defaultFieldCodeConfig(FormConfig typeFormConfig) {
        return defaultVocabularyOfFieldCode().map(vocabulary -> {
            ConceptFieldFormConfig config = new ConceptFieldFormConfig();
            config.setFormConfig(typeFormConfig);
            config.setVocabulary(vocabulary);
            config.setFieldCode(draftFieldCode);
            return config;
        });
    }

    public void searchValues() {
        browseValues(valuesQuery == null ? "" : valuesQuery, 0);
    }

    public void nextValuesPage() {
        browseValues(valuesQuery == null ? "" : valuesQuery, valuesOffset + VALUES_PAGE_SIZE);
    }

    public void previousValuesPage() {
        browseValues(valuesQuery == null ? "" : valuesQuery, Math.max(0, valuesOffset - VALUES_PAGE_SIZE));
    }

    public boolean isValuesHasPrevious() {
        return valuesOffset > 0;
    }

    public int getValuesFirstRank() {
        return valuesOffset + 1;
    }

    public int getValuesLastRank() {
        return valuesOffset + valuesItems.size();
    }

    /** The instances to pick from: those of the thesauri the database knows, plus the default one. */
    public List<String> getDraftServerOptions() {
        if (draftServerOptions == null) {
            Set<String> servers = new LinkedHashSet<>();
            servers.add(DEFAULT_THESAURUS_SERVER);
            vocabularyService.findAll().forEach(v -> servers.add(normalizeServer(v.getBaseUri())));
            draftServerOptions = new ArrayList<>(servers);
        }
        return draftServerOptions;
    }

    private static String normalizeServer(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private void selectServerOf(VocabularyDTO vocabulary) {
        draftServerUrl = normalizeServer(vocabulary.getBaseUri());
        draftThesaurus = null;
        if (!getDraftServerOptions().contains(draftServerUrl)) {
            draftServerOptions.add(draftServerUrl);
        }
        loadDraftThesaurusOptions();
        draftThesaurus = draftThesaurusOptions.stream()
                .filter(info -> info.idTheso().equals(vocabulary.getExternalVocabularyId()))
                .findFirst()
                .orElse(null);
    }

    private boolean loadDraftThesaurusOptions() {
        draftThesaurusOptions = new ArrayList<>();
        if (draftServerUrl == null || draftServerUrl.isBlank()) {
            return true;
        }
        try {
            draftThesaurusOptions = new ArrayList<>(
                    vocabularyService.findAllThesaurusOf(draftServerUrl, langBean.getLanguageCode()));
            return true;
        } catch (InvalidEndpointException e) {
            log.error("Could not list the thesauri of {}", draftServerUrl, e);
            return false;
        }
    }

    /** Another instance was picked: its thesauri are listed, and nothing is loaded until one is picked. */
    public void onDraftServerChange() {
        drawerErrorMessage = null;
        draftThesaurus = null;
        draftThesaurusUrl = null;
        draftConnectionTested = false;
        draftVocabulary = null;
        clearSourceSelection();
        if (!loadDraftThesaurusOptions()) {
            showDrawerError("projectTables.drawer.params.serverUnreachable");
        }
    }

    /** The instances matching what was typed. */
    public List<String> completeDraftServers(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        return getDraftServerOptions().stream().filter(server -> server.toLowerCase().contains(needle)).toList();
    }

    /** The thesauri of the picked instance matching what was typed, by label or identifier. */
    public List<ThesaurusInfo> completeDraftThesauri(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        return draftThesaurusOptions.stream()
                .filter(info -> info.label().toLowerCase().contains(needle) || info.idTheso().toLowerCase().contains(needle))
                .toList();
    }

    /** A thesaurus of the instance was picked: it is loaded so its branches and collections can be searched. */
    public void onDraftThesaurusChange() {
        clearSourceSelection();
        draftConnectionTested = false;
        draftVocabulary = null;
        draftThesaurusUrl = draftThesaurus == null || draftThesaurus.idTheso().isBlank()
                ? null : draftThesaurus.completeThesaurusUrl();
        if (draftThesaurusUrl != null) {
            testThesaurusConnection();
        }
    }

    /** Registers an instance typed by the user, once it answers, and selects it. */
    public void addDraftServer() {
        drawerErrorMessage = null;
        if (newServerUrl == null || newServerUrl.isBlank()) {
            return;
        }
        String server = normalizeServer(newServerUrl);
        try {
            vocabularyService.findAllThesaurusOf(server, langBean.getLanguageCode());
        } catch (InvalidEndpointException e) {
            log.error("Could not reach the OpenTheso instance {}", server, e);
            showDrawerError("projectTables.drawer.params.serverUnreachable");
            return;
        }
        if (!getDraftServerOptions().contains(server)) {
            draftServerOptions.add(server);
        }
        newServerUrl = null;
        draftServerUrl = server;
        onDraftServerChange();
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
            showDrawerError(INTERNAL_ERROR);
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
            showDrawerError(INTERNAL_ERROR);
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
        boolean created = draftOriginalName == null;
        if (isDraftMeasureSelectable()) {
            if (measureConceptIdOf(draftMeasureTypeLabel, lastMeasureTypeResults, draftMeasureType) == null) {
                showDrawerError("projectTables.drawer.measure.typeRequired");
                return;
            }
            draftName = measureName();
        }
        FieldOptions options = draftOptions();
        if (!draftIsSystem && draftType == FieldType.MEASUREMENT && options.allowedUnitIds().isEmpty()) {
            showDrawerError("projectTables.drawer.units.required");
            return;
        }
        formLayoutService.ensureOwnLayout(project.getId(), selectedTable, selectedTypeName);
        if (draftOriginalName == null) {
            tableFieldConfigService.createField(project.getId(), selectedTable, selectedTypeName, draftName, draftType, draftDescription, options);
        } else {
            tableFieldConfigService.updateField(project.getId(), selectedTable, selectedTypeName, draftOriginalName,
                    new FieldDefinition(draftName, draftType, draftDescription, options));
        }
        saveDraftUnits(options);
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
        } finally {
            progressWrapper.reset();
        }

        if (!saveDraftDefault()) {
            loadConfigs();
            return;
        }
        loadConfigs();
        if (created) {
            placeAfterAdd(draftName);
        }
        // the drawer stays open : the user can keep editing (or check what the vocabulary config
        // resolved to) without reopening it — re-reading it mirrors openDrawerForEdit's initial load,
        // so draftOriginalBrancheConceptKey/draftOriginalCollectionKey track what was just persisted
        // rather than the value the drawer was first opened with
        if (isDraftConfigurable()) {
            prefillVocabularyConfig();
        }
        MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "projectTables.drawer.saveSuccess");
        if (fieldsConfig != null) {
            fieldsConfig.getFields().stream()
                    .filter(f -> f.getName().equals(draftName))
                    .findFirst()
                    .ifPresent(this::announceOpenField);
        }
    }

    /**
     * Persists the vocabulary source picked in the drawer onto the field just saved by
     * {@link #saveDrawer()}: clears any existing branch/collection restriction for
     * the source back to the default, or saves the newly picked branch/collection/code — a no-op when the source is
     * unset (non-configurable fields) or when the user left the prefilled selection untouched.
     *
     * @throws FieldVocabularyConfigException when the picked branch/collection did not make it onto
     * the field, carrying the message {@link #saveDrawer()} reports
     */
    private void saveDraftVocabularyConfig() {
        if (BRANCHE.equals(draftSource)) {
            saveBrancheConceptIfChanged();
        } else if (COLLECTION.equals(draftSource)) {
            saveCollectionIfChanged();
        } else if (FIELD_CODE.equals(draftSource)) {
            saveFieldCodeIfChanged();
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
            formConfigService.addConceptConfigFor(target.formConfig(), target.field(), Objects.requireNonNull(draftBrancheConcept.concept()), progressWrapper);
            formConfigService.rememberSourceName(target.formConfig(), target.field(), draftBrancheConcept.getOriginalPrefLabel());
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
            formConfigService.addConceptConfigFor(target.formConfig(), target.field(), selected.get(), progressWrapper);
            formConfigService.rememberSourceName(target.formConfig(), target.field(), selected.get().getLabelToDisplay());
        } catch (RuntimeException e) {
            throw new VocabularyConfigNotSavedException(
                    String.format("Could not save the collection '%s' as the vocabulary of field '%s'",
                            selected.get().getExternalId(), draftName),
                    "projectTables.drawer.params.collectionSaveError", e);
        }
    }

    private void saveFieldCodeIfChanged() {
        if (draftCodeChoice == null || draftCodeChoice.isBlank() || draftVocabulary == null) {
            throw new NoVocabularySelectedException(
                    String.format("No field code picked for field '%s'", draftName),
                    "projectTables.drawer.params.codeMissing");
        }
        if (isDefaultFieldCodeChoice()) {
            // back to the field's own code in the default thesaurus: the field follows the default again
            clearConceptConfigIfAny();
            return;
        }
        if (codeKey(draftVocabulary, draftCodeChoice).equals(draftOriginalCodeKey)) {
            return;
        }
        ConceptFieldTarget target = resolveConceptFieldTarget("projectTables.drawer.params.codeSaveError");
        try {
            formConfigService.addFieldCodeConfigFor(target.formConfig(), target.field(),
                    vocabularyService.findVocabularyById(draftVocabulary.getId()), draftCodeChoice);
        } catch (RuntimeException e) {
            throw new VocabularyConfigNotSavedException(
                    String.format("Could not save the field code '%s' as the vocabulary of field '%s'", draftCodeChoice, draftName),
                    "projectTables.drawer.params.codeSaveError", e);
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

    /**
     * The types a field can be created as, one entry per family: "multiple values" is a checkbox
     * beside the type, not a type of its own.
     */
    public List<FieldType> getFieldTypeOptions() {
        return FieldType.userCreatableTypes().stream().filter(t -> !t.isMultiple()).toList();
    }

    /** The family the type menu shows for the open field (a multiple-valued type shows as its single sibling). */
    public String getDraftTypeName() {
        return draftType == null ? null : draftType.family().name();
    }

    public void setDraftTypeName(String name) {
        if (name == null) {
            draftType = null;
            return;
        }
        boolean multiple = draftType != null && draftType.isMultiple();
        FieldType family = FieldType.valueOf(name);
        draftType = multiple && family.supportsMultiple() ? family.multipleVariant() : family;
    }

    /**
     * JSF fills this list from the unit checkboxes with whatever type their item values have (the
     * units' Long ids), whatever the generic type says: they are kept as Strings here, which is
     * what every reader of the list compares with.
     */
    public void setDraftUnitIds(List<String> ids) {
        // read as List<?>: the elements may be Longs, and a String-typed read would cast each one
        List<?> raw = ids;
        draftUnitIds = raw == null ? new ArrayList<>()
                : raw.stream().map(String::valueOf).collect(Collectors.toCollection(ArrayList::new));
    }

    /** Whether the type menu's choice has a multiple-valued sibling, so the checkbox is offered. */
    public boolean isDraftSupportsMultiple() {
        return draftType != null && !draftIsSystem && draftType.family().supportsMultiple();
    }

    public boolean isDraftMultiple() {
        return draftType != null && draftType.isMultiple();
    }

    public void setDraftMultiple(boolean multiple) {
        if (draftType == null || !draftType.family().supportsMultiple()) return;
        draftType = multiple ? draftType.family().multipleVariant() : draftType.family();
    }

    /** Changing the type of a saved field makes a new field: the answers given so far are not carried over. */
    public boolean isDraftTypeChanged() {
        return draftOriginalType != null && draftType != null && draftOriginalType != draftType;
    }

    public boolean isDraftBoolean() {
        return draftType == FieldType.BOOLEAN;
    }

    /**
     * A measure added by the project is defined by what it measures and its nature, both picked in the
     * thesaurus: they replace the free name, which is the two put together.
     */
    public boolean isDraftMeasureSelectable() {
        return draftType == FieldType.MEASUREMENT && !draftIsSystem;
    }

    /**
     * A system measure shows what it measures and its nature, read-only: its name and description are
     * not its own to set. One with no concept for it is shown by its own name, with an empty nature.
     */
    public boolean isDraftDefinedByMeasureConcepts() {
        return draftType == FieldType.MEASUREMENT && draftIsSystem && draftOriginalName != null;
    }

    /** Whether the free name is hidden: a measure's name is built from its type and nature. */
    public boolean isDraftNameDerived() {
        return isDraftMeasureSelectable() || isDraftDefinedByMeasureConcepts();
    }

    /** What the system measure measures: its type concept, the field's own name when it has none. */
    public String getDraftMeasureTypeName() {
        return draftMeasureType != null ? draftMeasureType.name() : draftCaption();
    }

    public String getDraftMeasureNatureName() {
        return draftMeasureNature != null ? draftMeasureNature.name() : "";
    }

    public List<String> completeMeasureTypes(String query) {
        lastMeasureTypeResults = measureConcepts(CustomFieldMeasurement.MEASUREMENT_TYPE_FIELD_CODE, query);
        return lastMeasureTypeResults.stream().map(this::measureLabelOf).toList();
    }

    public List<String> completeMeasureNatures(String query) {
        lastMeasureNatureResults = measureConcepts(CustomFieldMeasurement.MEASUREMENT_NATURE_FIELD_CODE, query);
        return lastMeasureNatureResults.stream().map(this::measureLabelOf).toList();
    }

    private List<ConceptAutocompleteDTO> measureConcepts(String fieldCode, String query) {
        try {
            return new ArrayList<>(tableFieldConfigService.measureConcepts(project.getId(), fieldCode, query));
        } catch (RuntimeException e) {
            log.error("Could not fetch the concepts of {} matching '{}'", fieldCode, query, e);
            showDrawerError(INTERNAL_ERROR);
            return new ArrayList<>();
        }
    }

    private String measureLabelOf(ConceptAutocompleteDTO concept) {
        return concept.getConceptLabelToDisplay() != null ? concept.getConceptLabelToDisplay().getLabel() : concept.getOriginalPrefLabel();
    }

    /** The concept behind a label the user picked: a search result, or the concept the field already had. */
    private Long measureConceptIdOf(String label, List<ConceptAutocompleteDTO> results, ConceptSummary current) {
        if (label == null || label.isBlank()) return null;
        return results.stream().filter(c -> label.equals(measureLabelOf(c))).map(c -> c.concept().getId()).findFirst()
                .orElseGet(() -> current != null && label.equals(current.name()) ? current.id() : null);
    }

    /** The name of a measure: what it measures, then its nature. */
    private String measureName() {
        String nature = draftMeasureNatureLabel == null ? "" : draftMeasureNatureLabel.trim();
        String type = draftMeasureTypeLabel == null ? "" : draftMeasureTypeLabel.trim();
        return nature.isEmpty() ? type : type + " " + nature;
    }

    public boolean isDraftMeasurement() {
        return draftType == FieldType.MEASUREMENT;
    }

    /** One value the default can be picked among: a concept the field's list offers. */
    public record DefaultOption(String id, String label) implements java.io.Serializable {
    }

    /** The concepts a new entity starts with for the open field, by id; empty for no default. */
    private List<String> draftDefaultConceptIds = new ArrayList<>();

    /** The default stored when the drawer was opened, to tell "left untouched" from "changed". */
    private List<String> draftOriginalDefaultConceptIds = new ArrayList<>();

    public List<String> getDraftDefaultConceptIds() {
        return draftDefaultConceptIds;
    }

    public void setDraftDefaultConceptIds(List<String> ids) {
        draftDefaultConceptIds = ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    }

    /** The single-choice menu's value: the first concept of the default, null for none. */
    public String getDraftDefaultConceptId() {
        return draftDefaultConceptIds.isEmpty() ? null : draftDefaultConceptIds.get(0);
    }

    public void setDraftDefaultConceptId(String id) {
        draftDefaultConceptIds = id == null || id.isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(id));
    }

    /** A default is offered on a vocabulary field of an existing configuration, among the values its list offers. */
    public boolean isDraftDefaultAvailable() {
        return isDraftConfigurable() && draftOriginalName != null && valuesAvailable;
    }

    /** The values listed in the drawer, plus the stored default's when they are on another page. */
    public List<DefaultOption> getDefaultOptions() {
        Map<String, DefaultOption> options = new LinkedHashMap<>();
        for (ConceptAutocompleteDTO item : valuesItems) {
            if (item.getConceptLabelToDisplay() == null || item.getConceptLabelToDisplay().getConcept() == null) continue;
            String id = String.valueOf(item.getConceptLabelToDisplay().getConcept().getId());
            options.putIfAbsent(id, new DefaultOption(id, item.getConceptLabelToDisplay().getLabel()));
        }
        draftDefaultConceptIds.forEach(id -> options.putIfAbsent(id, new DefaultOption(id, "#" + id)));
        return new ArrayList<>(options.values());
    }

    private void loadDraftDefault() {
        draftDefaultConceptIds = new ArrayList<>();
        if (draftOriginalName != null && isDraftConfigurable()) {
            tableFieldConfigService.findField(project.getId(), selectedTable, selectedTypeName, draftOriginalName)
                    .flatMap(field -> formLayoutService.defaultOf(project.getId(), selectedTable, selectedTypeName, field.getId()))
                    .ifPresent(stored -> draftDefaultConceptIds = new ArrayList<>(stored.conceptIds()));
        }
        draftOriginalDefaultConceptIds = new ArrayList<>(draftDefaultConceptIds);
    }

    /**
     * Writes the default picked in the drawer onto the type's form configuration. A concept that is neither
     * on the page of values listed nor part of the stored default is refused: the list is what the field
     * offers, a default has to come from it. A single-choice field keeps only its first concept.
     */
    private boolean saveDraftDefault() {
        if (!isDraftConfigurable() || draftOriginalName == null) return true;
        List<String> picked = isDraftMultiple() || draftDefaultConceptIds.size() <= 1
                ? draftDefaultConceptIds : List.of(draftDefaultConceptIds.get(0));
        if (picked.equals(draftOriginalDefaultConceptIds)) return true;
        Set<String> offered = getDefaultOptions().stream().map(DefaultOption::id).collect(Collectors.toSet());
        if (!offered.containsAll(picked)) {
            showDrawerError("projectTables.drawer.default.notOffered");
            return false;
        }
        tableFieldConfigService.findField(project.getId(), selectedTable, selectedTypeName, draftOriginalName)
                .ifPresent(field -> formLayoutService.saveDefault(project.getId(), selectedTable, selectedTypeName,
                        field.getId(), FieldDefault.ofConceptIds(picked)));
        draftOriginalDefaultConceptIds = new ArrayList<>(picked);
        return true;
    }

    private void loadDraftOptions(FieldOptions options) {
        draftAllowNoAnswer = options.allowNoAnswer();
        draftUnitIds = options.allowedUnitIds().stream().map(String::valueOf).collect(Collectors.toCollection(ArrayList::new));
        draftDefaultUnitId = options.defaultUnitId() == null ? null : String.valueOf(options.defaultUnitId());
        draftDimension = unitCatalogue().stream()
                .filter(u -> options.defaultUnitId() != null && options.defaultUnitId().equals(u.getId()))
                .map(UnitDefinitionDTO::getDimension)
                .findFirst()
                .orElse(null);
    }

    /**
     * The units a measurement is entered in belong to the form configuration of the type, not to the
     * field: they are written there, for a system field as well as for one added by the project.
     */
    private void saveDraftUnits(FieldOptions options) {
        if (draftType != FieldType.MEASUREMENT || options.allowedUnitIds().isEmpty()) return;
        Map<Long, String> symbolById = new HashMap<>();
        unitCatalogue().forEach(u -> symbolById.put(u.getId(), u.getSymbol()));
        List<FieldUnits.UnitRef> units = options.allowedUnitIds().stream()
                .filter(symbolById::containsKey)
                .map(id -> new FieldUnits.UnitRef(id, symbolById.get(id)))
                .toList();
        tableFieldConfigService.findField(project.getId(), selectedTable, selectedTypeName, draftName)
                .ifPresent(field -> formLayoutService.saveUnits(project.getId(), selectedTable, selectedTypeName,
                        field.getId(), new FieldUnits(options.defaultUnitId(), units)));
    }

    private FieldOptions draftOptions() {
        if (draftType == FieldType.BOOLEAN) {
            return FieldOptions.noAnswer(draftAllowNoAnswer);
        }
        if (draftType == FieldType.MEASUREMENT) {
            Set<Long> ids = draftUnitIds.stream().map(Long::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
            Long defaultId = draftDefaultUnitId == null || draftDefaultUnitId.isBlank() ? null : Long.valueOf(draftDefaultUnitId);
            if (defaultId != null && !ids.contains(defaultId)) defaultId = null;
            if (defaultId == null && !ids.isEmpty()) defaultId = ids.iterator().next();
            return FieldOptions.units(defaultId, ids).measuring(
                    measureConceptIdOf(draftMeasureTypeLabel, lastMeasureTypeResults, draftMeasureType),
                    measureConceptIdOf(draftMeasureNatureLabel, lastMeasureNatureResults, draftMeasureNature));
        }
        return FieldOptions.NONE;
    }

    private List<UnitDefinitionDTO> unitCatalogue() {
        return unitDefinitionService == null ? List.of() : unitDefinitionService.findOptions();
    }

    /** What a measurement can measure: every dimension the unit catalogue has a unit for. */
    public List<UnitDefinition.Dimension> getDimensionOptions() {
        return unitCatalogue().stream().map(UnitDefinitionDTO::getDimension).distinct().sorted().toList();
    }

    public String dimensionLabel(UnitDefinition.Dimension dimension) {
        return langBean.msg("projectTables.drawer.dimension." + dimension.name());
    }

    /** The units of the chosen dimension, base unit first. */
    public List<UnitDefinitionDTO> getUnitOptions() {
        if (draftDimension == null) return List.of();
        return unitCatalogue().stream()
                .filter(u -> u.getDimension() == draftDimension)
                .sorted(Comparator.comparing(UnitDefinitionDTO::getFactorToBase))
                .toList();
    }

    public String getDraftDimensionName() {
        return draftDimension == null ? null : draftDimension.name();
    }

    /** Another dimension means other units: what was picked for the previous one is dropped. */
    public void setDraftDimensionName(String name) {
        UnitDefinition.Dimension chosen = name == null || name.isBlank() ? null : UnitDefinition.Dimension.valueOf(name);
        if (chosen != draftDimension) {
            draftUnitIds = new ArrayList<>();
            draftDefaultUnitId = null;
        }
        draftDimension = chosen;
    }

    /** The units picked so far, as the choices for the default unit. */
    public List<UnitDefinitionDTO> getPickedUnitOptions() {
        return getUnitOptions().stream().filter(u -> draftUnitIds.contains(String.valueOf(u.getId()))).toList();
    }


    public List<String> completeConfigurableTypes(String query) {
        return tableFieldConfigService.listConfigurableTypes(project.getId(), selectedTable, query);
    }

    public void addConfiguration() {
        tableFieldConfigService.addConfiguration(project.getId(), selectedTable, newTypeName);
        formLayoutService.ensureOwnLayout(project.getId(), selectedTable, newTypeName);
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
        IdentifierSegment segment = tokenSegment(code, numeric, IDENT_DEFAULT_DIGITS);
        if (isOwnNumericalToken(segment)) segment.setDigits(Math.max(IDENT_DEFAULT_DIGITS, requiredIdentDigits()));
        identSegments.add(segment);
    }

    /**
     * The maximum number changed: widen the padding of the table's own number so that it can hold it. A segment
     * without padding (0 digits) is left alone, and a wider padding is never narrowed.
     */
    public void onIdentLastChange() {
        int required = requiredIdentDigits();
        for (IdentifierSegment segment : identSegments) {
            if (isOwnNumericalToken(segment) && segment.getDigits() > 0 && segment.getDigits() < required) {
                segment.setDigits(required);
            }
        }
        identDigitsErrorIndex = null;
    }

    /**
     * @return The number of digits the maximum number takes, 0 when there is none.
     */
    private int requiredIdentDigits() {
        return identLast == null || identLast < 0 ? 0 : String.valueOf(identLast).length();
    }

    private boolean isOwnNumericalToken(IdentifierSegment segment) {
        return segment.isToken() && segment.isNumeric() && selectedTable != null
                && identifierResolverRegistry.ownNumericalToken(selectedTable).equals(segment.getCode());
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
            case DOCUMENT -> 15;
            case STUDY -> 4;
            case PROJECT -> 3;
        });
        values.put(ID_UA, "UA1");
        return new MapIdentifierRenderContext(values);
    }

    public void saveIdentConfig() {
        if (!isIdentTabAvailable()) return;

        identDigitsErrorIndex = null;
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
        int required = requiredIdentDigits();
        for (int i = 0; i < identSegments.size(); i++) {
            IdentifierSegment segment = identSegments.get(i);
            if (isOwnNumericalToken(segment) && segment.getDigits() > 0 && segment.getDigits() < required) {
                MessageUtils.displayErrorMessage(langBean, "projectTables.ident.insufficientDigits",
                        segment.getLabel(), segment.getDigits(), identLast, required);
                // the view highlights this segment's digit count and the save button's oncomplete focuses it
                identDigitsErrorIndex = i;
                return true;
            }
        }
        return false;
    }

    private Optional<IdentifierResolver<IdentifierRenderContext>> resolver(String code) {
        return selectedTable == null ? Optional.empty() : identifierResolverRegistry.resolver(selectedTable, code);
    }

}
