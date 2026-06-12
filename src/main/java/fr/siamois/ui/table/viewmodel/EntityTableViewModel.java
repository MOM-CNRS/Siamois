package fr.siamois.ui.table.viewmodel;

import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.view.FilterState;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.custom.LazyTreeMutator;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.fieldsource.TableRowFieldSource;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.FilterAndSortUtils;
import fr.siamois.ui.table.RowAction;
import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.ToolbarCreateConfig;
import fr.siamois.ui.table.column.*;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.event.ColumnToggleEvent;
import org.primefaces.model.TreeNode;
import org.primefaces.model.Visibility;
import org.primefaces.util.Callbacks;
import org.springframework.lang.NonNull;

import java.util.*;
import java.util.function.Function;

import static fr.siamois.dto.view.FilterState.FilterType.*;
import static fr.siamois.ui.bean.dialog.newunit.NewUnitContext.TreeInsert.ROOT;
import static fr.siamois.utils.MessageUtils.displayErrorMessage;

/**
 * View model générique pour une table d'entités avec formulaires dynamiques.
 * <p>
 * - tient une TableDefinition (colonnes)
 * - s'appuie sur un BaseLazyDataModel<T> "pur data"
 * - crée un EntityFormContext<T> par ligne :
 * - réponses
 * - règles d'activation
 * - champs système (min/max, etc.)
 * <p>
 * Les sous-classes doivent définir :
 * - resolveRowFormFor(T entity)
 * - configureRowSystemFields(T entity, CustomForm form)
 */
@Getter
public abstract class EntityTableViewModel<T extends AbstractEntityDTO, ID> {

    public static final String CONTAINER = "-container');";
    public static final int LIMIT = 100;
    public static final String LABEL = "label";
    @Setter
    protected String globalFilter = "";

    /**
     * Lazy model "pur data" (chargement, tri, filtres, sélection, etc.)
     */
    protected final BaseLazyDataModel<T> lazyDataModel;

    @Setter
    public int defaultPageSize = 20;

    /**
     * Services nécessaires pour la logique formulaire de ligne
     */
    protected final FormService formService;
    protected final SpatialUnitTreeService spatialUnitTreeService;
    protected final SpatialUnitService spatialUnitService;
    protected final NavBean navBean;
    protected final GenericNewUnitDialogBean<T> genericNewUnitDialogBean;
    protected final LangBean langBean;
    protected final FormContextServices formContextServices;

    @Getter
    @Setter
    private boolean columnFilteringEnabled = false;

    @Getter @Setter private String editingLinkValue;
    @Getter @Setter private Long editingLinkItemId;


    /**
     * Fournit l'identifiant unique d'une entité T (ex: RecordingUnit::getId)
     */
    private final Function<T, ID> idExtractor;
    @Setter
    protected AbstractPanel parentPanel; // the parent panel to open items in overview

    @Getter
    @Setter
    private Long overviewEntityId; // ID of the entity currently open in the overview panel

    /**
     * Nom de la propriété de T utilisée comme "form scope" (ex: "type"),
     * passé au EntityFormContext si besoin.
     */
    private final String formScopeValueBinding;

    /**
     * Définition globale des colonnes de la table
     */
    private final TableDefinition tableDefinition = new TableDefinition();

    /**
     * Contexte de formulaire par ligne (clé = ID de l'entité)
     */
    protected final Map<ID, EntityFormContext<T>> rowContexts = new HashMap<>();

    /**
     * Concepts sélectionnés pour le filtre d'une colonne (clé = valueBinding de la colonne).
     */
    private final Map<String, List<ConceptAutocompleteDTO>> conceptFilterValues = new HashMap<>();

    /**
     * Personnes sélectionnées pour le filtre d'une colonne (clé = valueBinding de la colonne).
     */
    private final Map<String, List<PersonDTO>> personFilterValues = new HashMap<>();

    /**
     * Action units sélectionnés pour le filtre d'une colonne (clé = valueBinding de la colonne).
     */
    private final Map<String, List<ActionUnitDTO>> actionUnitFilterValues = new HashMap<>();

    /**
     * Unités spatiales sélectionnées pour le filtre d'une colonne (clé = valueBinding de la colonne).
     */
    private final Map<String, List<SpatialUnitDTO>> spatialUnitFilterValues = new HashMap<>();

    /**
     * Bornes de dates sélectionnées pour le filtre d'une colonne (clé = valueBinding de la colonne).
     * Contient jusqu'à 2 éléments : [from, to].
     */
    private final Map<String, java.util.List<java.util.Date>> dateFilterValues = new HashMap<>();

    // tree mode selection
    @Setter
    @Getter
    private List<TreeNode<T>> checkboxSelectedTreeNodes;

    @Getter
    @Setter
    private ToolbarCreateConfig toolbarCreateConfig;

    @Getter
    @Setter
    protected boolean treeMode = false; // false = table, true = tree

    @Getter
    @Setter
    protected boolean isSwitchVisible = true;

    protected EntityTableViewModel(
            BaseLazyDataModel<T> lazyDataModel,
            GenericNewUnitDialogBean<T> genericNewUnitDialogBean,
            FormService formService,
            SpatialUnitTreeService spatialUnitTreeService,
            SpatialUnitService spatialUnitService,
            NavBean navBean,
            Function<T, ID> idExtractor,
            String formScopeValueBinding, LangBean langBean,
            FormContextServices formContextServices
    ) {
        this(
                lazyDataModel,
                genericNewUnitDialogBean,
                formService,
                spatialUnitTreeService,
                spatialUnitService,
                navBean, langBean,
                idExtractor,
                formScopeValueBinding,
                formContextServices
        );
    }

    protected EntityTableViewModel(
            BaseLazyDataModel<T> lazyDataModel,
            GenericNewUnitDialogBean<T> genericNewUnitDialogBean,
            FormService formService,
            SpatialUnitTreeService spatialUnitTreeService,
            SpatialUnitService spatialUnitService,
            NavBean navBean, LangBean langBean,
            Function<T, ID> idExtractor,
            String formScopeValueBinding,
            FormContextServices formContextServices
    ) {
        this.lazyDataModel = lazyDataModel;
        this.formService = formService;
        this.genericNewUnitDialogBean = genericNewUnitDialogBean;
        this.spatialUnitTreeService = spatialUnitTreeService;
        this.spatialUnitService = spatialUnitService;
        this.navBean = navBean;
        this.langBean = langBean;
        this.idExtractor = idExtractor;
        this.formScopeValueBinding = formScopeValueBinding;
        this.formContextServices = formContextServices;

        // Set global filter for both models if they exist
        if (this.lazyDataModel != null) {
            this.lazyDataModel.setGlobalFilter(globalFilter);
        }
    }


    /**
     * Colonnes visibles (pour <p:columns>).
     */
    public List<TableColumn> getColumns() {
        return tableDefinition.getColumns();
    }

    public boolean isConceptFilter(TableColumn column) {
        return column instanceof FormFieldColumn ffc
                && ffc.getField() instanceof CustomFieldSelectOneFromFieldCode;
    }

    public boolean isPersonFilter(TableColumn column) {
        return column instanceof FormFieldColumn ffc
                && ffc.getField() instanceof CustomFieldSelectPerson;
    }

    public boolean isActionUnitFilter(TableColumn column) {
        return column instanceof FormFieldColumn ffc
                && ffc.getField() instanceof CustomFieldSelectOneActionUnit;
    }

    public boolean isSpatialUnitFilter(TableColumn column) {
        return column instanceof FormFieldColumn ffc
                && ffc.getField() instanceof CustomFieldSelectOneSpatialUnit;
    }

    public boolean isDateTimeFilter(TableColumn column) {
        return column instanceof FormFieldColumn ffc
                && ffc.getField() instanceof CustomFieldDateTime;
    }

    public boolean isDateTimeShowTime(TableColumn column) {
        return column instanceof FormFieldColumn ffc
                && ffc.getField() instanceof CustomFieldDateTime dt
                && Boolean.TRUE.equals(dt.getShowTime());
    }

    public String getFieldCode(TableColumn column) {
        if (column instanceof FormFieldColumn ffc
                && ffc.getField() instanceof CustomFieldSelectOneFromFieldCode cf) {
            return cf.getFieldCode();
        }
        return null;
    }

    public List<PersonDTO> completePersonForFilter(String query) {
        return formContextServices.getSessionSettingsBean().completePerson(query);
    }

    public List<ActionUnitDTO> completeActionUnitForFilter(String query) {
        return formContextServices.getActionUnitService()
                .findMatchingInInstitutionByName(formContextServices.getSessionSettingsBean().getSelectedInstitution(), query, LIMIT);
    }

    public List<SpatialUnitDTO> completeSpatialUnitForFilter(String query) {
        return formContextServices.getSpatialUnitService()
                .findMatchingInInstitutionByName(formContextServices.getSessionSettingsBean().getSelectedInstitution(), query, LIMIT);
    }

    /**
     * Retourne (ou crée) le contexte de formulaire pour une ligne donnée.
     * Utilisable en EL : #{tableModel.rowContext(item)}
     */
    public EntityFormContext<T> getRowContext(T entity) {
        if (entity == null) {
            return null;
        }
        ID id = idExtractor.apply(entity);
        if (id == null) {
            return null;
        }

        return rowContexts.computeIfAbsent(id, key -> {
            // 1) Formulaire spécifique à cette entité (défini par la sous-classe)
            FormUiDto rowForm = resolveRowFormFor(entity);

            // 2) Configuration min/max des champs système pour CETTE ligne
            configureRowSystemFields(entity, rowForm);

            // 3) FieldSource pour cette ligne : colonnes de la table + form spécifique
            FieldSource fs = new TableRowFieldSource(tableDefinition, rowForm);

            // 4) Contexte de formulaire pour cette ligne
            EntityFormContext<T> ctx = new EntityFormContext<>(
                    entity,
                    fs,
                    formContextServices,
                    formContextServices.getConversionService(),
                    null,                    // pas de callback form scope en mode table pour l’instant
                    formScopeValueBinding
            );
            ctx.init(false);
            return ctx;
        });
    }

    /**
     * À appeler si tu veux "réinitialiser" les contextes de formulaire,
     * par exemple après un gros refresh de la liste.
     */
    public void resetRowContexts() {
        rowContexts.clear();
    }

    // ---------------------- Hooks à surcharger par les sous-classes ----------------------

    /**
     * Détermine le FormUiDto spécifique à une ligne d'entité T.
     * (ex: pour RecordingUnit, dépend du type + institution)
     */
    protected abstract FormUiDto resolveRowFormFor(T entity);

    /**
     * Applique la logique min/max sur les champs système pour une ligne donnée.
     * (ex: identifier, openingDate, closingDate pour RecordingUnit)
     */
    protected abstract void configureRowSystemFields(T entity, FormUiDto rowForm);

    // ---------------------- Helpers génériques ----------------------

    /**
     * Helper : récupère tous les CustomField d'un CustomForm (panels → rows → cols).
     * Indépendant du type T, donc factorisé ici.
     */
    protected List<CustomField> getAllFieldsFromForm(FormUiDto form) {
        if (form == null || form.getLayout() == null) {
            return List.of();
        }

        return form.getLayout().stream()
                .filter(panel -> panel.getRows() != null)
                .flatMap(panel -> panel.getRows().stream())
                .filter(row -> row.getColumns() != null)
                .flatMap(row -> row.getColumns().stream())
                .map(CustomColUiDto::getField)
                .filter(Objects::nonNull)
                .toList();
    }


    public void onToggle(ColumnToggleEvent e) {
        Integer index = (Integer) e.getData();
        Visibility visibility = e.getVisibility();
        // 4 bc the first 4 columns are fixed
        tableDefinition.getColumns().get(index - 2).setVisible(visibility == Visibility.VISIBLE);
    }


    public abstract String resolveText(TableColumn column, T item);

    public abstract Integer resolveCount(TableColumn column, T item);

    public abstract boolean isRendered(TableColumn column, String key, T item);

    public void handleAction(TableColumn column, T item) {
        if (column instanceof CommandLinkColumn linkColumn) {
            handleCommandLink(linkColumn, item);
        } else if (column instanceof RelationColumn relColumn) {
            handleRelationAction(relColumn, item, relColumn.getViewAction());
        }
    }

    public boolean isEditingLink(T item) {
        return item != null && item.getId().equals(editingLinkItemId);
    }

    public void startEditLink(T item) {
        this.editingLinkItemId = item.getId();
        this.editingLinkValue = resolveText(tableDefinition.getCommandLinkColumn(), item);
    }

    public void cancelEditLink() {
        this.editingLinkItemId = null;
        this.editingLinkValue = null;
    }

    public void applyLinkEdit(T item) {
        handleLinkEdit(tableDefinition.getCommandLinkColumn(), item, editingLinkValue);
        cancelEditLink();
    }

    public void handleLinkEdit(CommandLinkColumn column, T item, String newValue) {
        // no-op by default — subclasses override to persist
    }

    public void handleRelationAction(RelationColumn column, T item, TableColumnAction action) {
        // default no-op
    }

    protected void handleCommandLink(CommandLinkColumn column, T item) {
        // default no-op
    }

    public List<RowAction> getRowActions() {
        return List.of(); // par défaut aucune action
    }

    public Object getTreeRoot() {
        return null; // subclasses override if they support TREE mode
    }

    public String getRowActionTooltipCode(RowAction action, T unit) {
        return null; // no tooltip by default
    }

    public boolean isTreeViewSupported() {
        return false;
    }

    public void openCreateDialog(NewUnitContext ctx,
                                 fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean<T> dialogBean) {
        try {
            dialogBean.selectKind(ctx, this);
            org.primefaces.PrimeFaces.current().ajax().update("newUnitForm");
            org.primefaces.PrimeFaces.current().executeScript("PF('newUnitDiag').show()");
        } catch (fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException e) {
            // silent fail, same behavior as before
        }
    }


    public void onAnyEntityCreated(T created, NewUnitContext ctx) {
        if (created == null) return;

        NewUnitContext.UiInsertPolicy policy = ctx.getInsertPolicy();
        if (policy == null) { // if insert policy is null
            return;
        }

        if (treeMode) {
            // Tree view: mutate the displayed lazy tree directly so the user's
            // current page / expanded nodes are preserved. We deliberately
            // skip addRowToModel here — it overwrites the lazy model's
            // queryResult with just the new entity and would make the tree
            // collapse to a single row.
            if (policy.getTreeInsert() != NewUnitContext.TreeInsert.NONE) {
                applyTreeInsertion(created, ctx);
            }
        } else if (lazyDataModel != null
                && policy.getListInsert() != NewUnitContext.ListInsert.NONE) {
            // List view: insert at the top of the page.
            lazyDataModel.addRowToModel(created);
        }
    }


    @SuppressWarnings("unchecked")
    protected void applyTreeInsertion(T created, NewUnitContext ctx) {
        if (lazyDataModel == null) return;

        TreeNode<T> root = lazyDataModel.getLazyRoot();
        if (root == null) {
            // Tree hasn't been rendered yet — let next render fetch the new
            // entity from the database.
            return;
        }

        Object clickedId = (ctx.getTrigger() != null) ? ctx.getTrigger().getClickedId() : null;
        var treeInsert = ctx.getInsertPolicy().getTreeInsert();

        Callbacks.SerializableFunction<T, List<T>> loadFn =
                (Callbacks.SerializableFunction<T, List<T>>) (Callbacks.SerializableFunction<?, ?>) getLoadMethod();
        Callbacks.SerializableFunction<T, Boolean> isLeafFn =
                (Callbacks.SerializableFunction<T, Boolean>) getIsLeafMethod();

        if (clickedId == null || treeInsert == ROOT) {
            LazyTreeMutator.insertAtRoot(root, created, loadFn, isLeafFn);
            return;
        }

        switch (treeInsert) {
            case CHILD_FIRST -> LazyTreeMutator.insertChildFirst(root, clickedId, created, loadFn, isLeafFn);
            case PARENT_AT_ROOT -> LazyTreeMutator.insertParentAndReparent(root, clickedId, created, loadFn, isLeafFn);
            case SIBLING_BELOW -> LazyTreeMutator.insertSiblingBelow(root, clickedId, created, loadFn, isLeafFn);
            default -> {
                // no op (NONE)
            }
        }
    }

    // Handler when clicking on the create button on top of the table
    public void openCreateFromToolbar(fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean<T> dialogBean,
                                      String updateOnCreate,
                                      String tableClientId) {
        if (toolbarCreateConfig == null) {
            return; // pas de bouton configuré
        }

        NewUnitContext.UiInsertPolicy policy = toolbarCreateConfig.getInsertPolicySupplier() != null
                ? toolbarCreateConfig.getInsertPolicySupplier().get()
                : NewUnitContext.UiInsertPolicy.builder()
                .listInsert(NewUnitContext.ListInsert.TOP)
                .treeInsert(ROOT)
                .build();

        NewUnitContext ctx = NewUnitContext.builder()
                .kindToCreate(toolbarCreateConfig.getKindToCreate())
                .trigger(toolbarCreateConfig.getTriggerSupplier().get()) // toolbar()
                .scope(toolbarCreateConfig.getScopeSupplier().get())
                .insertPolicy(policy)
                .updateOnCreate(updateOnCreate)
                .tableClientId(tableClientId)
                .build();

        try {
            dialogBean.selectKind(ctx, this);
        } catch (fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException e) {
            // comportement silencieux comme avant
        }
    }

    public boolean hasUnsavedModifications() {
        return rowContexts.values().stream()
                .anyMatch(EntityFormContext::isHasUnsavedModifications);
    }

    /**
     * Save all the entities of the table
     */
    public void save() {
        // warning message saying not implemented
        displayErrorMessage(langBean, "common.error.savingNotImplemented");
    }

    /**
     * Cancel any modifs
     */
    public void cancelModifications() {
        rowContexts.values().forEach(ctx -> {
            ctx.init(true);
            ctx.setHasUnsavedModifications(false);
        });
    }

    /*
     * Check if user has permission to edit the row data
     */
    public abstract boolean canUserEditRow(T unit);

    public String getOnCompleteJs(T unit) {
        if (unit instanceof RecordingUnitDTO) {
            return "PF('buiContent').hide();onCompleteCallback('panel-recording-unit-" + unit.getId() + CONTAINER;
        } else if (unit instanceof SpecimenDTO) {
            return "PF('buiContent').hide();onCompleteCallback('panel-specimen-" + unit.getId() + CONTAINER;
        } else if (unit instanceof ActionUnitDTO) {
            return "PF('buiContent').hide();onCompleteCallback('panel-action-unit-" + unit.getId() + CONTAINER;
        } else if (unit instanceof SpatialUnitDTO) {
            return "PF('buiContent').hide();onCompleteCallback('panel-spatial-unit-" + unit.getId() + CONTAINER;
        } else if (unit instanceof ContainerDTO) {
            return "PF('buiContent').hide();onCompleteCallback('panel-container-" + unit.getId() + CONTAINER;
        } else if (unit instanceof PhaseDTO) {
            return "PF('buiContent').hide();onCompleteCallback('panel-phase-" + unit.getId() + CONTAINER;
        } else {
            throw new IllegalArgumentException("Non handled type  : " + unit.getClass().getName());
        }
    }

    public abstract BaseLazyDataModel<T> getLazyDataModel();

    @SuppressWarnings({"unchecked", "unused"})
    public Callbacks.SerializableFunction<AbstractEntityDTO, Boolean> getIsLeafMethod() {
        return abstractEntityDTO -> {
            if (abstractEntityDTO == null) return true;
            return unitIsLeaf((T) abstractEntityDTO);
        };
    }

    @SuppressWarnings({"unchecked", "unused"})
    public Callbacks.SerializableFunction<AbstractEntityDTO, List<AbstractEntityDTO>> getLoadMethod() {
        return parentUnit -> {
            if (parentUnit == null) {
                return new ArrayList<>();
            }
            List<T> children = loadChildrensOfUnit((T) parentUnit);
            Set<Long> closure = lazyDataModel != null ? lazyDataModel.getAncestorClosure() : null;
            // When the parent IS a search match, every descendant belongs to
            // the result — don't filter to the closure (which only contains
            // matches + their ancestors), or non-matching children silently
            // disappear and the node looks like a leaf.
            Set<Long> matches = lazyDataModel != null ? lazyDataModel.getMatchIds() : null;
            ID parentId = idExtractor.apply((T) parentUnit);
            boolean parentIsMatch = matches != null && parentId != null && matches.contains(parentId);
            if (closure != null && !parentIsMatch) {
                children = children.stream()
                        .filter(c -> closure.contains(idExtractor.apply(c)))
                        .toList();
            }
            return (List<AbstractEntityDTO>) children;
        };
    }

    protected abstract boolean unitIsLeaf(@NonNull T unit);

    @NonNull
    protected abstract List<T> loadChildrensOfUnit(@NonNull T parentUnit);

    public void onSwitchTreeMode() {
        this.lazyDataModel.setRootOnly(!this.lazyDataModel.isRootOnly());
        this.lazyDataModel.setLazyRoot(null);
        this.lazyDataModel.resetCache();
    }

    /**
     * @return {@code true} if {@code item} is a row that actually matched the
     * current search (its id is in the lazy model's {@code matchIds} set), as
     * opposed to a pure ancestor brought in only to keep the path visible.
     */
    public boolean isSearchMatch(T item) {
        if (item == null || lazyDataModel == null) return false;
        Set<Long> matches = lazyDataModel.getMatchIds();
        if (matches == null || matches.isEmpty()) return false;
        ID id = idExtractor.apply(item);
        return id != null && matches.contains(id);
    }

    /**
     * Row CSS class: {@code search-match} for search hits, {@code overview-open} for
     * the entity currently open in the side overview.
     */
    public String getRowStyleClass(T item) {
        if (item == null) return "";
        String classes = isSearchMatch(item) ? "search-match" : "";
        if (overviewEntityId != null && overviewEntityId.equals(item.getId())) {
            classes = classes.isEmpty() ? "overview-open" : classes + " overview-open";
        }
        return classes;
    }

    /**
     * Update the entity in the lazy model cache and reset its row context so the
     * next render shows the latest data without a DB round-trip.
     */
    public void updateEntityInCurrentPage(T updatedEntity) {
        if (updatedEntity == null) return;
        ID id = idExtractor.apply(updatedEntity);
        if (id == null) return;
        if (lazyDataModel != null) {
            lazyDataModel.updateEntityInCache(updatedEntity);
        }
        rowContexts.remove(id);
    }

    /**
     * Updates the entity in the cache and drops its row context only if it is present
     * in the current page. Safe to call with any AbstractEntityDTO due to type erasure —
     * no actual cast failure can occur when just replacing in List and removing from Map.
     */
    @SuppressWarnings("unchecked")
    public void updateIfPresent(AbstractEntityDTO entity) {
        if (entity == null || entity.getId() == null) return;
        if (getRowIndexInCurrentPage(entity.getId()) < 0) return;
        if (lazyDataModel != null) {
            lazyDataModel.updateEntityInCache((T) entity);
        }
        rowContexts.remove(entity.getId());
    }

    /**
     * Index (0-based) of {@code entityId} in the current page's cached result, or -1 if absent.
     * Used to build a PrimeFaces {@code :@row(n)} AJAX update target.
     */
    public int getRowIndexInCurrentPage(Long entityId) {
        if (lazyDataModel == null || entityId == null) return -1;
        List<T> result = lazyDataModel.getQueryResult();
        if (result == null) return -1;
        for (int i = 0; i < result.size(); i++) {
            if (entityId.equals(result.get(i).getId())) return i;
        }
        return -1;
    }

    /**
     * Re-render the table when {@code columnFilteringEnabled} flips. Without
     * this, {@code LazyTreeTable.preEncode} keeps the cached {@code lazyRoot},
     * so the previously-filtered result stays on screen even though
     * {@code loadLazyData} would now skip the filter map. The plain dataTable
     * has no equivalent shell, so we also propagate the flag to the lazy
     * model where {@code load}/{@code count} can short-circuit the filters.
     */
    public void onColumnFilteringToggle() {
        if (lazyDataModel != null) {
            lazyDataModel.setColumnFilteringEnabled(columnFilteringEnabled);
            lazyDataModel.setLazyRoot(null);
            lazyDataModel.resetCache();
        }
    }


    @SuppressWarnings("unchecked")
    private List<Date> castDateList(Object value) {

        if (value == null) {
            return List.of();
        }

        if (value instanceof List<?> list) {

            return list.stream()
                    .filter(Objects::nonNull)
                    .map(v -> {
                        if (v instanceof Number n) {
                            return new Date(n.longValue());
                        }
                        if (v instanceof String s) {
                            return new Date(Long.parseLong(s));
                        }
                        throw new IllegalArgumentException(
                                "Invalid date value: " + v
                        );
                    })
                    .toList();
        }

        throw new IllegalArgumentException(
                "Expected List for DATE_RANGE but got: " + value.getClass()
        );
    }

    @SuppressWarnings("unchecked")
    public void applyFilterStates(
            Map<String, FilterState> filters
    ) {

        if (filters == null) {
            return;
        }

        // Apply to table
        conceptFilterValues.clear();
        personFilterValues.clear();
        actionUnitFilterValues.clear();
        spatialUnitFilterValues.clear();
        dateFilterValues.clear();

        for (FilterState state : filters.values()) {

            if (state == null) {
                continue;
            }

            switch (state.getType()) {

                case CONCEPT -> {

                    List<Map<String, Object>> rawValues =
                            (List<Map<String, Object>>) state.getValue();

                    List<ConceptAutocompleteDTO> values =
                            rawValues.stream()
                                    .map(raw -> {

                                        Long id =
                                                ((Number) raw.get("id")).longValue();

                                        String label =
                                                (String) raw.get(LABEL);

                                        ConceptDTO concept =
                                                new ConceptDTO();

                                        concept.setId(id);

                                        return new ConceptAutocompleteDTO(
                                                concept,
                                                label,
                                                langBean.getLanguageCode()
                                        );
                                    })
                                    .toList();

                    conceptFilterValues.put(
                            state.getColumnId(),
                            values
                    );
                }

                case PERSON -> {

                    List<Map<String, Object>> rawValues =
                            (List<Map<String, Object>>) state.getValue();

                    List<PersonDTO> values =
                            rawValues.stream()
                                    .map(raw -> {

                                        Long id =
                                                ((Number) raw.get("id")).longValue();

                                        String label =
                                                (String) raw.get(LABEL);

                                        PersonDTO dto =
                                                new PersonDTO();

                                        dto.setId(id);

                                        // fake display label
                                        dto.setName(label);

                                        return dto;
                                    })
                                    .toList();

                    personFilterValues.put(
                            state.getColumnId(),
                            values
                    );
                }

                case ACTION_UNIT -> {

                    List<Map<String, Object>> rawValues =
                            (List<Map<String, Object>>) state.getValue();

                    List<ActionUnitDTO> values =
                            rawValues.stream()
                                    .map(raw -> {

                                        Long id =
                                                ((Number) raw.get("id")).longValue();

                                        String label =
                                                (String) raw.get(LABEL);

                                        ActionUnitDTO dto =
                                                new ActionUnitDTO();

                                        dto.setId(id);

                                        dto.setName(label);

                                        return dto;
                                    })
                                    .toList();

                    actionUnitFilterValues.put(
                            state.getColumnId(),
                            values
                    );
                }

                case SPATIAL_UNIT -> {

                    List<Map<String, Object>> rawValues =
                            (List<Map<String, Object>>) state.getValue();

                    List<SpatialUnitDTO> values =
                            rawValues.stream()
                                    .map(raw -> {

                                        Long id =
                                                ((Number) raw.get("id")).longValue();

                                        String label =
                                                (String) raw.get(LABEL);

                                        SpatialUnitDTO dto =
                                                new SpatialUnitDTO();

                                        dto.setId(id);

                                        dto.setName(label);

                                        return dto;
                                    })
                                    .toList();

                    spatialUnitFilterValues.put(
                            state.getColumnId(),
                            values
                    );
                }

                case DATE_RANGE -> {

                    List<Date> dates =
                            castDateList(state.getValue());

                    dateFilterValues.put(
                            state.getColumnId(),
                            dates
                    );
                }

                default -> throw new IllegalArgumentException("Invalid value for column: " + state.getColumnId());
            }
        }

        // Init lazy
        lazyDataModel.setInitialFilter(
                FilterAndSortUtils.toFilterMetaMap(filters)
        );
    }

    public Map<String, FilterState> extractFilterStates() {

        Map<String, FilterState> filters = new HashMap<>();

        // concepts
        conceptFilterValues.forEach((columnId, values) -> {

            FilterState state = new FilterState();

            state.setColumnId(columnId);

            state.setType(CONCEPT);

            state.setValue(
                    values.stream()
                            .map(v -> Map.of(
                                    "id", v.concept().getId(),
                                    LABEL, v.getConceptLabelToDisplay().getLabel()
                            ))
                            .toList()
            );

            filters.put(columnId, state);
        });

        // persons
        personFilterValues.forEach((columnId, values) -> {

            FilterState state = new FilterState();

            state.setColumnId(columnId);

            state.setType(PERSON);

            state.setValue(
                    values.stream()
                            .map(v -> Map.of(
                                    "id", v.getId(),
                                    LABEL, v.displayName()
                            ))
                            .toList()
            );

            filters.put(columnId, state);
        });

        // action units
        actionUnitFilterValues.forEach((columnId, values) -> {

            FilterState state = new FilterState();

            state.setColumnId(columnId);

            state.setType(ACTION_UNIT);

            state.setValue(
                    values.stream()
                            .map(v -> Map.of(
                                    "id", v.getId(),
                                    LABEL, v.getName()
                            ))
                            .toList()
            );

            filters.put(columnId, state);
        });

        // spatial units
        spatialUnitFilterValues.forEach((columnId, values) -> {

            FilterState state = new FilterState();

            state.setColumnId(columnId);

            state.setType(SPATIAL_UNIT);

            state.setValue(
                    values.stream()
                            .map(v -> Map.of(
                                    "id", v.getId(),
                                    LABEL, v.getName()
                            ))
                            .toList()
            );

            filters.put(columnId, state);
        });

        // date ranges
        dateFilterValues.forEach((columnId, values) -> {

            FilterState state = new FilterState();

            state.setColumnId(columnId);

            state.setType(DATE_RANGE);

            state.setValue(values);

            filters.put(columnId, state);
        });

        return filters;
    }

    public String getSelectedAndTotalCount() {
        return lazyDataModel.getSelectedUnits().size() + "/" + lazyDataModel.getRowCount();
    }

    public void handleSelectionChange() {
        // Empty action handler for selection changes
    }
}
